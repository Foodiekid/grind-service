package com.grindandtrain.core.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.core.account.repository.AccountRepository;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.common.config.CoreProperties;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.common.storage.BlobStorage;
import com.grindandtrain.core.support.GrindTestDatabase;
import com.grindandtrain.core.sync.domain.BlobReference;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.core.sync.repository.RecordRepository;
import com.grindandtrain.core.sync.repository.StorageUsageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The sync housekeeping jobs' logic on a real Postgres as the application role: orphaned blobs are found and only
 * they are deleted, usage drift is corrected, and the cross-user user listing returns ids while row-level security
 * still hides everyone's rows.
 *
 * @author Dheeraj_Edupuganti
 */
class SyncMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private JdbcClient jdbcClient;
    private UserTransactionTemplate userTransactions;
    private StorageUsageRepository storageUsageRepository;
    private BlobStorage blobStorage;
    private AccountService accountService;
    private RecordService recordService;
    private SyncMaintenanceService maintenance;

    @BeforeEach
    void setUp() {
        var dataSource = GrindTestDatabase.get().appDataSource();
        jdbcClient = JdbcClient.create(dataSource);
        userTransactions = new UserTransactionTemplate(jdbcClient,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        RecordRepository recordRepository = new RecordRepository(jdbcClient);
        storageUsageRepository = new StorageUsageRepository(jdbcClient);
        blobStorage = mock(BlobStorage.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        accountService = new AccountService(userTransactions,
                new AccountRepository(jdbcClient), eventPublisher, new AuditLogger());
        CoreProperties properties = new CoreProperties(null,
                new CoreProperties.Limits(4_096, 8_388_608, 1_000_000), new CoreProperties.Messaging("grind-events"));
        recordService = new RecordService(userTransactions, recordRepository, storageUsageRepository, accountService,
                blobStorage, eventPublisher, properties);
        maintenance = new SyncMaintenanceService(userTransactions, recordRepository, storageUsageRepository, blobStorage);
    }

    @Test
    void deletesOnlyOldBlobsThatNoRecordPointsTo() {
        UserId user = newUser();
        String committed = BlobStorage.userPrefix(user) + "committed";
        String orphan = BlobStorage.userPrefix(user) + "orphan";
        String recent = BlobStorage.userPrefix(user) + "recent";
        store(user, new EncryptedRecord(RecordId.of(UUID.randomUUID()), 1, false, null,
                new BlobReference(committed, 100, "s".repeat(44)), "wrapped-key"));
        Instant old = NOW.minus(30, ChronoUnit.DAYS);
        given(blobStorage.listBlobs(user)).willReturn(List.of(
                new BlobStorage.StoredBlob(committed, old),
                new BlobStorage.StoredBlob(orphan, old),
                new BlobStorage.StoredBlob(recent, NOW.minus(1, ChronoUnit.HOURS))));

        int deleted = maintenance.deleteOrphanedBlobs(user, NOW.minus(14, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(1);
        verify(blobStorage).delete(orphan);
        verify(blobStorage, never()).delete(committed);
        verify(blobStorage, never()).delete(recent);
    }

    @Test
    void userWithoutOldBlobsTouchesNothing() {
        UserId user = newUser();
        given(blobStorage.listBlobs(user)).willReturn(List.of());

        assertThat(maintenance.deleteOrphanedBlobs(user, NOW)).isZero();
        verify(blobStorage, never()).delete(anyString());
    }

    @Test
    void correctsUsageThatDrifted() {
        UserId user = newUser();
        store(user, inline(40));
        store(user, inline(25));
        userTransactions.executeWithoutResult(user, () -> storageUsageRepository.updateUsedBytes(user, 999));

        long correction = maintenance.reconcileUsage(user);

        assertThat(correction).isEqualTo(65 - 999);
        assertThat(usedBytes(user)).isEqualTo(65);
        assertThat(maintenance.reconcileUsage(user)).isZero();
    }

    @Test
    void listsUsersAcrossAccountsButRowLevelSecurityStillHidesTheirRows() {
        UserId first = newUser();
        UserId second = newUser();
        store(first, inline(10));
        store(second, inline(10));

        List<UserId> users = pageThroughAllUsers();

        assertThat(users).contains(first, second);
        // Outside a user's transaction, the application role still can't read anyone's usage directly.
        assertThat(jdbcClient.sql("select count(*) from storage_usage").query(Long.class).single()).isZero();
    }

    private List<UserId> pageThroughAllUsers() {
        var all = new java.util.ArrayList<UserId>();
        UserId after = UserId.of(new UUID(0, 0));
        List<UserId> page;
        do {
            page = maintenance.usersAfter(after, 2);
            all.addAll(page);
            if (!page.isEmpty()) {
                after = page.getLast();
            }
        } while (!page.isEmpty());
        return all;
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

    private static EncryptedRecord inline(int bytes) {
        return new EncryptedRecord(RecordId.of(UUID.randomUUID()), 1, false, new byte[bytes], null, "wrapped-key");
    }
}
