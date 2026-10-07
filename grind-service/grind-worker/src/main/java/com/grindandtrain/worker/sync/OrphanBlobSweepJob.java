package com.grindandtrain.worker.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.stream.Stream;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.sync.service.SyncMaintenanceService;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.common.jobs.JobReport;
import com.grindandtrain.worker.common.jobs.ScheduledJob;

import org.springframework.stereotype.Component;

/**
 * Daily: deletes uploaded blobs that no record points to (the upload's commit never happened), user by user. Only
 * blobs older than {@code grind.worker.jobs.orphan-blob-age} are considered, so uploads still on their way are safe.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class OrphanBlobSweepJob implements ScheduledJob {

    private final SyncMaintenanceService maintenance;
    private final WorkerProperties properties;
    private final Clock clock;

    public OrphanBlobSweepJob(SyncMaintenanceService maintenance, WorkerProperties properties, Clock clock) {
        this.maintenance = maintenance;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "orphan-blob-sweep";
    }

    @Override
    public JobReport run(Instant deadline) {
        Instant uploadedBefore = Instant.now(clock).minus(properties.jobs().orphanBlobAge());
        int examined = 0;
        int deleted = 0;
        try (Stream<UserId> users = maintenance.usersWithBlobs()) {
            Iterator<UserId> remaining = users.iterator();
            while (remaining.hasNext() && Instant.now(clock).isBefore(deadline)) {
                deleted += maintenance.deleteOrphanedBlobs(remaining.next(), uploadedBefore);
                examined++;
            }
            return new JobReport(name(), examined, deleted, !remaining.hasNext());
        }
    }
}
