package com.grindandtrain.worker.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.sync.service.SyncMaintenanceService;
import com.grindandtrain.worker.account.AccountDeletionSweepJob;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.sync.OrphanBlobSweepJob;
import com.grindandtrain.worker.sync.StorageUsageReconcileJob;

import org.junit.jupiter.api.Test;

/**
 * The three housekeeping jobs: what they hand to grind-core, what they report, and that they stop at their deadline
 * and leave the rest for the next run.
 *
 * @author Dheeraj_Edupuganti
 */
class ScheduledJobsTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(20));

    private final WorkerProperties properties = new WorkerProperties(
            new WorkerProperties.PubSub("a", "p"),
            new WorkerProperties.Scheduler("a", "s"),
            new WorkerProperties.Supabase("https://p.supabase.co", "k", Duration.ofSeconds(2), Duration.ofSeconds(10)),
            new WorkerProperties.Jobs(Duration.ofMinutes(20), Duration.ofDays(14), Duration.ofHours(24)));
    private final SyncMaintenanceService maintenance = mock(SyncMaintenanceService.class);
    private final AccountService accountService = mock(AccountService.class);

    @Test
    void orphanSweepVisitsEveryUserWithTheAgeCutOff() {
        UserId first = user();
        UserId second = user();
        given(maintenance.usersWithBlobs()).willReturn(Stream.of(first, second));
        given(maintenance.deleteOrphanedBlobs(first, NOW.minus(Duration.ofDays(14)))).willReturn(2);

        JobReport report = new OrphanBlobSweepJob(maintenance, properties, CLOCK).run(LATER);

        assertThat(report).isEqualTo(new JobReport("orphan-blob-sweep", 2, 2, true));
    }

    @Test
    void jobsStopAtTheDeadline() {
        given(maintenance.usersWithBlobs()).willReturn(Stream.of(user(), user()));

        JobReport report = new OrphanBlobSweepJob(maintenance, properties, CLOCK).run(NOW);

        assertThat(report.examined()).isZero();
        assertThat(report.finished()).isFalse();
        verify(maintenance, never()).deleteOrphanedBlobs(any(), any());
    }

    @Test
    void usageReconcilePagesThroughEveryUser() {
        UserId first = user();
        UserId second = user();
        given(maintenance.usersAfter(any(), anyInt())).willReturn(List.of(first, second), List.of());
        given(maintenance.reconcileUsage(first)).willReturn(-120L);
        given(maintenance.reconcileUsage(second)).willReturn(0L);

        JobReport report = new StorageUsageReconcileJob(maintenance, CLOCK).run(LATER);

        assertThat(report).isEqualTo(new JobReport("storage-usage-reconcile", 2, 1, true));
        verify(maintenance).usersAfter(second, 500);
    }

    @Test
    void deletionSweepResumesEveryStuckDeletion() {
        given(accountService.findStuckDeletions(NOW.minus(Duration.ofHours(24)), 500)).willReturn(List.of(user(), user()));

        JobReport report = new AccountDeletionSweepJob(accountService, properties, CLOCK).run(LATER);

        assertThat(report).isEqualTo(new JobReport("account-deletion-sweep", 2, 2, true));
        verify(accountService, times(2)).resumeDeletion(any());
    }

    private static UserId user() {
        return UserId.of(UUID.randomUUID());
    }
}
