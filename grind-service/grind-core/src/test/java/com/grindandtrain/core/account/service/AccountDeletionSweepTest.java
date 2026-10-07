package com.grindandtrain.core.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.core.account.repository.AccountRepository;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.support.GrindTestDatabase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The deletion sweeper's logic on a real Postgres as the application role: it finds deletions that never completed,
 * across users, skips completed and recent ones, and resuming publishes the event again.
 *
 * @author Dheeraj_Edupuganti
 */
class AccountDeletionSweepTest {

    private JdbcClient jdbcClient;
    private EventPublisher eventPublisher;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        var dataSource = GrindTestDatabase.get().appDataSource();
        jdbcClient = JdbcClient.create(dataSource);
        var userTransactions = new UserTransactionTemplate(jdbcClient,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        eventPublisher = mock(EventPublisher.class);
        accountService = new AccountService(userTransactions, new AccountRepository(jdbcClient), eventPublisher,
                new AuditLogger());
    }

    @Test
    void findsStuckDeletionsAcrossUsersButNotCompletedOnes() {
        UserId stuck = UserId.of(UUID.randomUUID());
        UserId completed = UserId.of(UUID.randomUUID());
        accountService.requestDeletion(stuck);
        accountService.requestDeletion(completed);
        accountService.markDeletionCompleted(completed);

        var found = accountService.findStuckDeletions(Instant.now().plus(1, ChronoUnit.HOURS), 1000);

        assertThat(found).contains(stuck).doesNotContain(completed);
        // The application role still can't read the table across users.
        assertThat(jdbcClient.sql("select count(*) from accounts").query(Long.class).single()).isZero();
    }

    @Test
    void recentDeletionsAreLeftAlone() {
        UserId recent = UserId.of(UUID.randomUUID());
        accountService.requestDeletion(recent);

        var found = accountService.findStuckDeletions(Instant.now().minus(1, ChronoUnit.HOURS), 1000);

        assertThat(found).doesNotContain(recent);
    }

    @Test
    void resumingPublishesTheDeletionAgain() {
        UserId user = UserId.of(UUID.randomUUID());

        accountService.resumeDeletion(user);

        verify(eventPublisher).publish(any(AccountDeletionRequestedEvent.class));
    }
}
