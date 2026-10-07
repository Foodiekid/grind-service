package com.grindandtrain.core.sync.service;

import java.util.List;
import java.util.Optional;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.common.config.CoreProperties;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.common.storage.BlobStorage;
import com.grindandtrain.core.sync.domain.ChangePage;
import com.grindandtrain.core.sync.domain.DownloadLink;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.core.sync.domain.RecordChange;
import com.grindandtrain.core.sync.repository.RecordRepository;
import com.grindandtrain.core.sync.repository.RecordRepository.StoredVersion;
import com.grindandtrain.core.sync.repository.StorageUsageRepository;
import com.grindandtrain.contract.event.sync.RecordCommittedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Synchronisation of encrypted records.
 * <p>
 * The API calls {@link #submit}, which validates a revision and publishes it. The worker calls {@link #store}, which
 * writes it. The server only ever sees ids, revision numbers, sizes and ciphertext.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class RecordService {

    private static final Logger log = LoggerFactory.getLogger(RecordService.class);

    private final UserTransactionTemplate userTransactions;
    private final RecordRepository recordRepository;
    private final StorageUsageRepository storageUsageRepository;
    private final AccountService accountService;
    private final BlobStorage blobStorage;
    private final EventPublisher eventPublisher;
    private final CoreProperties.Limits limits;

    public RecordService(UserTransactionTemplate userTransactions, RecordRepository recordRepository,
            StorageUsageRepository storageUsageRepository, AccountService accountService, BlobStorage blobStorage,
            EventPublisher eventPublisher, CoreProperties properties) {
        this.userTransactions = userTransactions;
        this.recordRepository = recordRepository;
        this.storageUsageRepository = storageUsageRepository;
        this.accountService = accountService;
        this.blobStorage = blobStorage;
        this.eventPublisher = eventPublisher;
        this.limits = properties.limits();
    }

    /** Validates a revision and publishes it. Resubmitting the stored revision is accepted without publishing again. */
    public void submit(UserId userId, EncryptedRecord record) {
        accountService.ensureActive(userId);
        validateShape(userId, record);

        Optional<StoredVersion> stored = userTransactions.execute(userId,
                () -> recordRepository.findVersion(userId, record.id()));
        if (stored.isPresent()) {
            if (stored.get().revision() > record.revision()) {
                throw new GrindException(BusinessErrorCode.STALE_REVISION);
            }
            if (stored.get().revision() == record.revision()) {
                return;
            }
        }
        if (record.blob() != null) {
            long uploadedSize = blobStorage.size(record.blob().key())
                    .orElseThrow(() -> new GrindException(BusinessErrorCode.BLOB_MISSING));
            if (uploadedSize != record.blob().size()) {
                throw new GrindException(BusinessErrorCode.BLOB_SIZE_MISMATCH);
            }
        }
        long used = userTransactions.execute(userId, () -> storageUsageRepository.findUsedBytes(userId));
        long replacedBytes = stored.map(StoredVersion::storedBytes).orElse(0L);
        if (exceedsQuota(used, replacedBytes, record.storedBytes())) {
            throw new GrindException(BusinessErrorCode.QUOTA_EXCEEDED);
        }
        eventPublisher.publish(RecordEventMapper.toEvent(userId, record));
    }

    /**
     * Writes a published revision if it is newer than the stored one and still fits the quota, then deletes the blob
     * nothing points to.
     * <p>
     * The quota is checked again here, with the user's usage row locked: the API's check can't see writes still in
     * flight. A revision that no longer fits is dropped (its blob deleted); the app re-submits revisions it doesn't
     * see in sync-down and then gets {@code quota_exceeded} from the API.
     */
    public void store(RecordCommittedEvent event) {
        UserId userId = UserId.of(event.userId());
        EncryptedRecord record = RecordEventMapper.toRecord(event);
        String incomingBlobKey = record.blob() == null ? null : record.blob().key();

        String unreferencedBlobKey = userTransactions.execute(userId, () -> {
            if (accountService.isDeletionRequested(userId)) {
                return incomingBlobKey; // the account is being deleted: keep nothing
            }
            Optional<StoredVersion> stored = recordRepository.findVersionForUpdate(userId, record.id());
            if (stored.isPresent() && stored.get().revision() >= record.revision()) {
                // stale or repeated delivery: only the incoming blob may now be unreferenced
                return isDifferent(incomingBlobKey, stored.get().blobKey()) ? incomingBlobKey : null;
            }
            long used = storageUsageRepository.lockUsedBytes(userId);
            long replacedBytes = stored.map(StoredVersion::storedBytes).orElse(0L);
            if (exceedsQuota(used, replacedBytes, record.storedBytes())) {
                log.warn("Revision dropped: storage quota reached");
                return incomingBlobKey;
            }
            recordRepository.upsert(userId, record);
            storageUsageRepository.updateUsedBytes(userId, used - replacedBytes + record.storedBytes());
            String replacedBlobKey = stored.map(StoredVersion::blobKey).orElse(null);
            return isDifferent(replacedBlobKey, incomingBlobKey) ? replacedBlobKey : null;
        });
        if (unreferencedBlobKey != null) {
            blobStorage.delete(unreferencedBlobKey);
        }
    }

    public ChangePage findChanges(UserId userId, String cursor, int limit) {
        long afterSequence = parseCursor(cursor);
        List<RecordChange> changes = userTransactions.execute(userId,
                () -> recordRepository.findChangesAfter(userId, afterSequence, limit + 1));
        boolean hasMore = changes.size() > limit;
        List<RecordChange> page = hasMore ? changes.subList(0, limit) : changes;
        String nextCursor = page.isEmpty() ? String.valueOf(afterSequence) : String.valueOf(page.getLast().sequence());
        return new ChangePage(List.copyOf(page), nextCursor, hasMore);
    }

    public DownloadLink createBlobDownload(UserId userId, RecordId recordId) {
        String blobKey = userTransactions.execute(userId, () -> recordRepository.findLiveBlobKey(userId, recordId))
                .orElseThrow(() -> new GrindException(BusinessErrorCode.NOT_FOUND));
        BlobStorage.PresignedDownload download = blobStorage.presignDownload(blobKey);
        return new DownloadLink(download.url(), download.expiresAt());
    }

    /** Deletes all of a user's blobs and records. Used when the account is deleted; safe to repeat. */
    public void deleteAllForUser(UserId userId) {
        blobStorage.deleteByPrefix(BlobStorage.userPrefix(userId));
        userTransactions.executeWithoutResult(userId, () -> {
            recordRepository.deleteAllByUser(userId);
            storageUsageRepository.deleteByUser(userId);
        });
    }

    private void validateShape(UserId userId, EncryptedRecord record) {
        boolean hasInline = record.inlineCiphertext() != null;
        boolean hasBlob = record.blob() != null;
        if (record.deleted()) {
            if (hasInline || hasBlob || record.wrappedKey() != null) {
                throw new GrindException(BusinessErrorCode.INVALID_RECORD);
            }
            return;
        }
        if (hasInline == hasBlob || record.wrappedKey() == null) {
            throw new GrindException(BusinessErrorCode.INVALID_RECORD);
        }
        if (hasInline && record.inlineCiphertext().length > limits.maxInlineBytes()) {
            throw new GrindException(BusinessErrorCode.INLINE_TOO_LARGE);
        }
        if (hasBlob && !record.blob().key().startsWith(BlobStorage.userPrefix(userId))) {
            throw new GrindException(BusinessErrorCode.BLOB_NOT_OWNED);
        }
    }

    /** Only growth is limited: shrinking or deleting a record is always allowed, even over the quota. */
    private boolean exceedsQuota(long usedBytes, long replacedBytes, long newBytes) {
        return newBytes > replacedBytes && usedBytes - replacedBytes + newBytes > limits.quotaBytes();
    }

    private static long parseCursor(String cursor) {
        if (cursor == null) {
            return 0;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new GrindException(BusinessErrorCode.INVALID_CURSOR);
        }
    }

    private static boolean isDifferent(String key, String other) {
        return key != null && !key.equals(other);
    }
}
