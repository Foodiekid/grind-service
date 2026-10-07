package com.grindandtrain.core.common.persistence;

import java.util.function.Supplier;

import com.grindandtrain.common.domain.UserId;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs database work on behalf of one user.
 * <p>
 * Each call opens (or joins) a transaction and sets {@code app.user_id}, which the row-level security policies in
 * V1__init.sql check. A mistake in a query's WHERE clause therefore still can't reach another user's rows. The
 * setting is transaction-local, which also makes it safe behind Neon's connection pooler.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class UserTransactionTemplate {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    public UserTransactionTemplate(JdbcClient jdbcClient, TransactionTemplate transactionTemplate) {
        this.jdbcClient = jdbcClient;
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T execute(UserId userId, Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            jdbcClient.sql("select set_config('app.user_id', :userId, true)")
                    .param("userId", userId.value().toString())
                    .query(String.class)
                    .single();
            return action.get();
        });
    }

    public void executeWithoutResult(UserId userId, Runnable action) {
        execute(userId, () -> {
            action.run();
            return null;
        });
    }
}
