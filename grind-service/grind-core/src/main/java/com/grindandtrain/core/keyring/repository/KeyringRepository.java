package com.grindandtrain.core.keyring.repository;

import java.util.Map;
import java.util.Optional;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.keyring.domain.StoredKeyring;
import com.grindandtrain.core.keyring.domain.WrappedMasterKey;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@code keyrings}. Use inside
 * {@link com.grindandtrain.core.common.persistence.UserTransactionTemplate}.
 *
 * @author Dheeraj_Edupuganti
 */
@Repository
public class KeyringRepository {

    private final JdbcClient jdbcClient;

    public KeyringRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<StoredKeyring> findByUser(UserId userId) {
        return jdbcClient.sql("""
                select rev, format_version, kdf_salt, kdf_iterations, wrapped_by_passphrase, wrapped_by_recovery_key, check_value
                from keyrings
                where user_id = :userId""")
                .param("userId", userId.value())
                .query((rs, rowNum) -> new StoredKeyring(rs.getLong("rev"), new WrappedMasterKey(
                        rs.getInt("format_version"), rs.getString("kdf_salt"), rs.getInt("kdf_iterations"),
                        rs.getString("wrapped_by_passphrase"), rs.getString("wrapped_by_recovery_key"),
                        rs.getString("check_value"))))
                .optional();
    }

    /** Creates the first keyring. False when the user already has one. */
    public boolean insert(UserId userId, WrappedMasterKey key) {
        return jdbcClient.sql("""
                insert into keyrings (user_id, rev, format_version, kdf_salt, kdf_iterations,
                                      wrapped_by_passphrase, wrapped_by_recovery_key, check_value)
                values (:userId, 1, :formatVersion, :kdfSalt, :kdfIterations,
                        :wrappedByPassphrase, :wrappedByRecoveryKey, :checkValue)
                on conflict (user_id) do nothing""")
                .params(columns(userId, key))
                .update() == 1;
    }

    /** Replaces the keyring only if it is still at {@code expectedRevision}. False when someone changed it meanwhile. */
    public boolean updateIfRevisionMatches(UserId userId, long expectedRevision, WrappedMasterKey key) {
        return jdbcClient.sql("""
                update keyrings set
                    rev = rev + 1,
                    format_version = :formatVersion,
                    kdf_salt = :kdfSalt,
                    kdf_iterations = :kdfIterations,
                    wrapped_by_passphrase = :wrappedByPassphrase,
                    wrapped_by_recovery_key = :wrappedByRecoveryKey,
                    check_value = :checkValue,
                    updated_at = now()
                where user_id = :userId and rev = :expectedRevision""")
                .params(columns(userId, key))
                .param("expectedRevision", expectedRevision)
                .update() == 1;
    }

    public void deleteByUser(UserId userId) {
        jdbcClient.sql("delete from keyrings where user_id = :userId").param("userId", userId.value()).update();
    }

    private static Map<String, Object> columns(UserId userId, WrappedMasterKey key) {
        return Map.of(
                "userId", userId.value(),
                "formatVersion", key.formatVersion(),
                "kdfSalt", key.kdfSalt(),
                "kdfIterations", key.kdfIterations(),
                "wrappedByPassphrase", key.wrappedByPassphrase(),
                "wrappedByRecoveryKey", key.wrappedByRecoveryKey(),
                "checkValue", key.checkValue());
    }
}
