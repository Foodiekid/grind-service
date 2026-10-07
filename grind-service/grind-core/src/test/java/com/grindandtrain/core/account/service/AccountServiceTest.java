package com.grindandtrain.core.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.core.account.repository.AccountRepository;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.support.GrindTestDatabase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The account row on a real Postgres as the application role: it is created once on the first write, also when
 * several first requests arrive together, and once a deletion is requested every later write is refused, even after
 * the deletion completed.
 *
 * @author Dheeraj_Edupuganti
 */
class AccountServiceTest {

    private UserTransactionTemplate userTransactions;
    private JdbcClient jdbcClient;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        var dataSource = GrindTestDatabase.get().appDataSource();
        jdbcClient = JdbcClient.create(dataSource);
        userTransactions = new UserTransactionTemplate(jdbcClient,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        accountService = new AccountService(userTransactions, new AccountRepository(jdbcClient),
                mock(EventPublisher.class), new AuditLogger());
    }

    @Test
    void firstWriteCreatesTheAccountOnce() {
        UserId user = newUser();

        accountService.ensureActive(user);
        accountService.ensureActive(user);

        assertThat(accountRows(user)).isEqualTo(1);
        assertThat(accountService.isDeletionRequested(user)).isFalse();
    }

    @Test
    void simultaneousFirstRequestsCreateOneAccountAndAllSucceed() throws Exception {
        UserId user = newUser();
        int requests = 16;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    accountService.ensureActive(user);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        }

        assertThat(accountRows(user)).isEqualTo(1);
    }

    @Test
    void writesAreRefusedFromTheDeletionRequestOnward() {
        UserId user = newUser();
        accountService.ensureActive(user);

        accountService.requestDeletion(user);
        assertThatThrownBy(() -> accountService.ensureActive(user))
                .isInstanceOf(GrindException.class)
                .extracting(e -> ((GrindException) e).errorCode())
                .isEqualTo(BusinessErrorCode.ACCOUNT_DELETED);

        accountService.markDeletionCompleted(user);
        assertThatThrownBy(() -> accountService.ensureActive(user)).isInstanceOf(GrindException.class);
        assertThat(accountService.isDeletionRequested(user)).isTrue();
    }

    @Test
    void deletionRequestedBeforeAnyWriteStillRefusesLaterWrites() {
        UserId user = newUser();

        accountService.requestDeletion(user);

        assertThatThrownBy(() -> accountService.ensureActive(user)).isInstanceOf(GrindException.class);
    }

    @Test
    void repeatingTheRequestNeverReopensACompletedDeletion() {
        UserId user = newUser();
        accountService.requestDeletion(user);
        accountService.markDeletionCompleted(user);

        accountService.requestDeletion(user);

        String status = userTransactions.execute(user, () -> jdbcClient
                .sql("select status from accounts where user_id = :userId")
                .param("userId", user.value()).query(String.class).single());
        assertThat(status).isEqualTo("deleted");
    }

    private long accountRows(UserId user) {
        return userTransactions.execute(user, () -> jdbcClient.sql("select count(*) from accounts")
                .query(Long.class).single());
    }

    private static UserId newUser() {
        return UserId.of(UUID.randomUUID());
    }
}
