package com.grindandtrain.core.sync.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.common.storage.BlobStorage;
import com.grindandtrain.core.sync.repository.RecordRepository;
import com.grindandtrain.core.sync.repository.StorageUsageRepository;

import org.springframework.stereotype.Service;

/**
 * Housekeeping for synced data, run by the worker's scheduled jobs one user at a time, each step inside that user's
 * own transaction (row-level security on). Every operation is safe to repeat and to interrupt, so a job can stop at
 * its time budget and the next run carries on.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class SyncMaintenanceService {

    private final UserTransactionTemplate userTransactions;
    private final RecordRepository recordRepository;
    private final StorageUsageRepository storageUsageRepository;
    private final BlobStorage blobStorage;

    public SyncMaintenanceService(UserTransactionTemplate userTransactions, RecordRepository recordRepository,
            StorageUsageRepository storageUsageRepository, BlobStorage blobStorage) {
        this.userTransactions = userTransactions;
        this.recordRepository = recordRepository;
        this.storageUsageRepository = storageUsageRepository;
        this.blobStorage = blobStorage;
    }

    /** Every user who has blobs in storage, read lazily. */
    public Stream<UserId> usersWithBlobs() {
        return blobStorage.usersWithBlobs();
    }

    /**
     * Deletes the user's blobs uploaded before {@code uploadedBefore} that no record points to: uploads whose commit
     * never happened. The cut-off must be older than any event still in flight (Pub/Sub keeps them up to 7 days), or a
     * late commit could find its blob gone.
     *
     * @return how many blobs were deleted
     */
    public int deleteOrphanedBlobs(UserId userId, Instant uploadedBefore) {
        List<BlobStorage.StoredBlob> oldBlobs = blobStorage.listBlobs(userId).stream()
                .filter(blob -> blob.uploadedAt().isBefore(uploadedBefore))
                .toList();
        if (oldBlobs.isEmpty()) {
            return 0;
        }
        Set<String> referenced = userTransactions.execute(userId, () -> recordRepository.findBlobKeys(userId));
        int deleted = 0;
        for (BlobStorage.StoredBlob blob : oldBlobs) {
            if (!referenced.contains(blob.key())) {
                blobStorage.delete(blob.key());
                deleted++;
            }
        }
        return deleted;
    }

    /** The next page of users who store data, in id order; start with the nil UUID. */
    public List<UserId> usersAfter(UserId afterUser, int maxRows) {
        return storageUsageRepository.findUsersAfter(afterUser, maxRows);
    }

    /**
     * Recomputes the user's stored bytes from their records and corrects the usage row if it drifted. The usage row
     * is locked while this runs, so a concurrent write waits instead of racing.
     *
     * @return the correction applied (0 when the usage was right)
     */
    public long reconcileUsage(UserId userId) {
        return userTransactions.execute(userId, () -> {
            long recorded = storageUsageRepository.lockUsedBytes(userId);
            long actual = recordRepository.sumStoredBytes(userId);
            if (actual != recorded) {
                storageUsageRepository.updateUsedBytes(userId, actual);
            }
            return actual - recorded;
        });
    }
}
