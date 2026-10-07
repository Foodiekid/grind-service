package com.grindandtrain.core.account.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.account.domain.AccountStatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@code accounts}, the parent row of every user's data.
 *
 * <p>A row holds the Supabase user id, a status, a plan and dates; never an email or a name. It is created on the
 * user's first request and kept after the account is deleted, so a late write from another device is refused
 * instead of recreating data. Use inside {@link com.grindandtrain.core.common.persistence.UserTransactionTemplate}.
 *
 * @author Dheeraj_Edupuganti
 */
@Repository
public class AccountRepository {

    private final JdbcClient jdbcClient;

    public AccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Creates the account if it doesn't exist yet and returns its status. Safe when several first requests arrive at
     * once: one insert wins, the others skip theirs, and the read that follows (a new statement, so a fresh snapshot)
     * sees the winner's row. A single statement could not: its snapshot predates the winner's commit.
     */
    public AccountStatus createIfAbsentAndGetStatus(UserId userId) {
        jdbcClient.sql("insert into accounts (user_id) values (:userId) on conflict (user_id) do nothing")
                .param("userId", userId.value())
                .update();
        return jdbcClient.sql("select status from accounts where user_id = :userId")
                .param("userId", userId.value())
                .query(String.class)
                .single()
                .transform(AccountStatus::fromDatabase);
    }

    /** The account's status, or empty before the user's first request. */
    public Optional<AccountStatus> findStatus(UserId userId) {
        return jdbcClient.sql("select status from accounts where user_id = :userId")
                .param("userId", userId.value())
                .query(String.class)
                .optional()
                .map(AccountStatus::fromDatabase);
    }

    /** Marks an active (or not yet created) account as being deleted. Repeating it changes nothing. */
    public void markDeleting(UserId userId) {
        jdbcClient.sql("""
                insert into accounts (user_id, status, deletion_requested_at) values (:userId, 'deleting', now())
                on conflict (user_id) do update
                    set status = 'deleting', deletion_requested_at = now(), updated_at = now()
                    where accounts.status = 'active'""")
                .param("userId", userId.value())
                .update();
    }

    /**
     * Deletions requested before {@code requestedBefore} and still not complete, oldest first. Cross-user, so it goes
     * through the {@code stuck_account_deletions} function, which returns ids only; call it outside any user
     * transaction.
     */
    public List<UserId> findStuck(Instant requestedBefore, int maxRows) {
        return jdbcClient.sql("select stuck_account_deletions(:requestedBefore, :maxRows)")
                .param("requestedBefore", OffsetDateTime.ofInstant(requestedBefore, ZoneOffset.UTC))
                .param("maxRows", maxRows)
                .query(UUID.class)
                .list()
                .stream()
                .map(UserId::of)
                .toList();
    }

    public void markDeleted(UserId userId) {
        jdbcClient.sql("""
                update accounts set status = 'deleted', deletion_completed_at = now(), updated_at = now()
                where user_id = :userId and status = 'deleting'""")
                .param("userId", userId.value())
                .update();
    }
}
