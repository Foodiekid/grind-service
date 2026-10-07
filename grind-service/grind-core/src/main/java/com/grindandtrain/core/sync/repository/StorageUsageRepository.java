package com.grindandtrain.core.sync.repository;

import java.util.List;
import java.util.UUID;

import com.grindandtrain.common.domain.UserId;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@code storage_usage}: one row per user with the bytes they store. Use inside
 * {@link com.grindandtrain.core.common.persistence.UserTransactionTemplate}.
 *
 * @author Dheeraj_Edupuganti
 */
@Repository
public class StorageUsageRepository {

    private final JdbcClient jdbcClient;

    public StorageUsageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Bytes the user stores; 0 before their first write. */
    public long findUsedBytes(UserId userId) {
        return jdbcClient.sql("select used_bytes from storage_usage where user_id = :userId")
                .param("userId", userId.value())
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    /**
     * Same as {@link #findUsedBytes}, locking the user's row until the transaction ends, so concurrent writes by the
     * same user are checked one after the other. Creates the row on first use.
     */
    public long lockUsedBytes(UserId userId) {
        jdbcClient.sql("insert into storage_usage (user_id) values (:userId) on conflict (user_id) do nothing")
                .param("userId", userId.value())
                .update();
        return jdbcClient.sql("select used_bytes from storage_usage where user_id = :userId for update")
                .param("userId", userId.value())
                .query(Long.class)
                .single();
    }

    public void updateUsedBytes(UserId userId, long usedBytes) {
        jdbcClient.sql("update storage_usage set used_bytes = :usedBytes, updated_at = now() where user_id = :userId")
                .param("userId", userId.value())
                .param("usedBytes", usedBytes)
                .update();
    }

    /**
     * The next page of users who store data, after {@code afterUser} in id order. Cross-user, so it goes through the
     * {@code storage_users_after} function, which returns ids only; call it outside any user transaction.
     */
    public List<UserId> findUsersAfter(UserId afterUser, int maxRows) {
        return jdbcClient.sql("select storage_users_after(:afterUser, :maxRows)")
                .param("afterUser", afterUser.value())
                .param("maxRows", maxRows)
                .query(UUID.class)
                .list()
                .stream()
                .map(UserId::of)
                .toList();
    }

    public void deleteByUser(UserId userId) {
        jdbcClient.sql("delete from storage_usage where user_id = :userId").param("userId", userId.value()).update();
    }
}
