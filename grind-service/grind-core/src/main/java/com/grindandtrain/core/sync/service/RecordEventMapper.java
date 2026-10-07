package com.grindandtrain.core.sync.service;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.sync.domain.BlobReference;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.contract.event.sync.RecordCommittedEvent;

/**
 * Converts between the sync domain and the {@link RecordCommittedEvent} payload. The event is a published contract
 * and changes on its own schedule, so the domain type is never sent as is.
 *
 * @author Dheeraj_Edupuganti
 */
final class RecordEventMapper {

    private RecordEventMapper() {
    }

    static RecordCommittedEvent toEvent(UserId userId, EncryptedRecord record) {
        BlobReference blob = record.blob();
        return new RecordCommittedEvent(
                userId.value(),
                record.id().value(),
                record.revision(),
                record.deleted(),
                record.inlineCiphertext(),
                blob == null ? null : blob.key(),
                blob == null ? null : blob.size(),
                blob == null ? null : blob.sha256(),
                record.wrappedKey());
    }

    static EncryptedRecord toRecord(RecordCommittedEvent event) {
        BlobReference blob = event.blobKey() == null ? null
                : new BlobReference(event.blobKey(), event.blobSize(), event.blobSha256());
        return new EncryptedRecord(
                RecordId.of(event.recordId()),
                event.revision(),
                event.deleted(),
                event.inlineCiphertext(),
                blob,
                event.wrappedKey());
    }
}
