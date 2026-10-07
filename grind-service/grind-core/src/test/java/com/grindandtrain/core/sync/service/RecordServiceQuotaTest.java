package com.grindandtrain.core.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.core.account.repository.AccountRepository;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.common.config.CoreProperties;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.common.storage.BlobStorage;
import com.grindandtrain.core.support.GrindTestDatabase;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.core.sync.repository.RecordRepository;
import com.grindandtrain.core.sync.repository.StorageUsageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The storage quota on a real Postgres, as the application role (row-level security on): usage is tracked on every
 * write, the API's check reads one row, the worker's check holds under concurrent writes, and one user can't see
 * another's usage.
 *
 * @author Dheeraj_Edupuganti
 */
class RecordServiceQuotaTest {

    private static final long QUOTA_BYTES = 100;

    private UserTransactionTemplate userTransactions;
    private StorageUsageRepository storageUsageRepository;
    private AccountService accountService;
    private RecordService recordService;

    @BeforeEach
    void setUp() {
        var dataSource = GrindTestDatabase.get().appDataSource();
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        userTransactions = new UserTransactionTemplate(jdbcClient,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        storageUsageRepository = new StorageUsageRepository(jdbcClient);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        accountService = new AccountService(userTransactions,
                new AccountRepository(jdbcClient), eventPublisher, new AuditLogger());
        CoreProperties properties = new CoreProperties(null,
                new CoreProperties.Limits(4_096, 8_388_608, QUOTA_BYTES), new CoreProperties.Messaging("grind-events"));
        recordService = new RecordService(userTransactions, new RecordRepository(jdbcClient), storageUsageRepository,
                accountService, mock(BlobStorage.class), eventPublisher, properties);
    }

    @Test
    void usageFollowsEveryWriteReplacementAndDeletion() {
        UserId user = newUser();
        RecordId record = RecordId.of(UUID.randomUUID());

        store(user, inline(record, 1, 40));
        assertThat(usedBytes(user)).isEqualTo(40);

        store(user, inline(record, 2, 10));
        assertThat(usedBytes(user)).isEqualTo(10);

        store(user, new EncryptedRecord(record, 3, true, null, null, null));
        assertThat(usedBytes(user)).isZero();
    }

    @Test
    void repeatedOrStaleDeliveryDoesNotCountTwice() {
        UserId user = newUser();
        RecordId record = RecordId.of(UUID.randomUUID());

        store(user, inline(record, 2, 30));
        store(user, inline(record, 2, 30));
        store(user, inline(record, 1, 50));

        assertThat(usedBytes(user)).isEqualTo(30);
    }

    @Test
    void apiRejectsAWriteThatWouldExceedTheQuota() {
        UserId user = newUser();
        RecordId existing = RecordId.of(UUID.randomUUID());
        store(user, inline(existing, 1, 80));

        assertThatThrownBy(() -> recordService.submit(user, inline(RecordId.of(UUID.randomUUID()), 1, 30)))
                .isInstanceOf(GrindException.class)
                .extracting(e -> ((GrindException) e).errorCode())
                .isEqualTo(BusinessErrorCode.QUOTA_EXCEEDED);

        // Replacing a record only counts the difference: 80 becomes 95, which fits.
        recordService.submit(user, inline(existing, 2, 95));
    }

    @Test
    void workerDropsARevisionThatNoLongerFits() {
        UserId user = newUser();
        store(user, inline(RecordId.of(UUID.randomUUID()), 1, 80));

        store(user, inline(RecordId.of(UUID.randomUUID()), 1, 30));

        assertThat(usedBytes(user)).isEqualTo(80);
        assertThat(recordService.findChanges(user, null, 10).changes()).hasSize(1);
    }

    @Test
    void shrinkingIsAllowedEvenOverTheQuota() {
        UserId user = newUser();
        RecordId record = RecordId.of(UUID.randomUUID());
        store(user, inline(record, 1, 100));

        store(user, inline(record, 2, 60));

        assertThat(usedBytes(user)).isEqualTo(60);
    }

    @Test
    void concurrentWritesCannotRacePastTheQuota() throws Exception {
        UserId user = newUser();
        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> writes = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            EncryptedRecord record = inline(RecordId.of(UUID.randomUUID()), 1, 30);
            writes.add(() -> {
                start.await();
                store(user, record);
                return null;
            });
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
            List<Future<Void>> results = new ArrayList<>();
            for (Callable<Void> write : writes) {
                results.add(executor.submit(write));
            }
            start.countDown();
            for (Future<Void> result : results) {
                result.get();
            }
        }

        // 30-byte records under a 100-byte quota: exactly three fit, whatever the timing.
        assertThat(usedBytes(user)).isEqualTo(90);
        assertThat(recordService.findChanges(user, null, 20).changes()).hasSize(3);
    }

    @Test
    void oneUserCannotSeeAnothersUsage() {
        UserId owner = newUser();
        UserId other = newUser();
        store(owner, inline(RecordId.of(UUID.randomUUID()), 1, 50));

        long seenByOther = userTransactions.execute(other, () -> storageUsageRepository.findUsedBytes(owner));

        assertThat(seenByOther).isZero();
        assertThat(usedBytes(owner)).isEqualTo(50);
    }

    @Test
    void accountDeletionRemovesTheUsageRow() {
        UserId user = newUser();
        store(user, inline(RecordId.of(UUID.randomUUID()), 1, 50));

        recordService.deleteAllForUser(user);

        assertThat(usedBytes(user)).isZero();
        assertThat(recordService.findChanges(user, null, 10).changes()).isEmpty();
    }

    private void store(UserId user, EncryptedRecord record) {
        recordService.store(RecordEventMapper.toEvent(user, record));
    }

    private long usedBytes(UserId user) {
        return userTransactions.execute(user, () -> storageUsageRepository.findUsedBytes(user));
    }

    /** A user whose account exists, as the API makes sure before any of their records reaches the worker. */
    private UserId newUser() {
        UserId user = UserId.of(UUID.randomUUID());
        accountService.ensureActive(user);
        return user;
    }

    private static EncryptedRecord inline(RecordId id, long revision, int bytes) {
        return new EncryptedRecord(id, revision, false, new byte[bytes], null, "wrapped-key");
    }
}
