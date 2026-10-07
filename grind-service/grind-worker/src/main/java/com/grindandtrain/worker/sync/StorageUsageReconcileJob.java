package com.grindandtrain.worker.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.sync.service.SyncMaintenanceService;
import com.grindandtrain.worker.common.jobs.JobReport;
import com.grindandtrain.worker.common.jobs.ScheduledJob;

import org.springframework.stereotype.Component;

/**
 * Weekly: recomputes every user's stored bytes from their records and corrects any drift in the quota usage, so a
 * bug or a manual fix can never leave someone blocked (or unlimited) for good.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class StorageUsageReconcileJob implements ScheduledJob {

    private static final int PAGE_SIZE = 500;

    private final SyncMaintenanceService maintenance;
    private final Clock clock;

    public StorageUsageReconcileJob(SyncMaintenanceService maintenance, Clock clock) {
        this.maintenance = maintenance;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "storage-usage-reconcile";
    }

    @Override
    public JobReport run(Instant deadline) {
        int examined = 0;
        int corrected = 0;
        UserId after = UserId.of(new UUID(0, 0));
        while (Instant.now(clock).isBefore(deadline)) {
            List<UserId> page = maintenance.usersAfter(after, PAGE_SIZE);
            if (page.isEmpty()) {
                return new JobReport(name(), examined, corrected, true);
            }
            for (UserId user : page) {
                if (!Instant.now(clock).isBefore(deadline)) {
                    return new JobReport(name(), examined, corrected, false);
                }
                if (maintenance.reconcileUsage(user) != 0) {
                    corrected++;
                }
                examined++;
                after = user;
            }
        }
        return new JobReport(name(), examined, corrected, false);
    }
}
