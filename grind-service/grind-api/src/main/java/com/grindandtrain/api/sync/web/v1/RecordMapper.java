package com.grindandtrain.api.sync.web.v1;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.contract.api.v1.dto.BlobRef;
import com.grindandtrain.contract.api.v1.dto.DownloadTicket;
import com.grindandtrain.contract.api.v1.dto.RecordEnvelope;
import com.grindandtrain.contract.api.v1.dto.RecordPage;
import com.grindandtrain.contract.api.v1.dto.RecordWrite;
import com.grindandtrain.core.sync.domain.BlobReference;
import com.grindandtrain.core.sync.domain.ChangePage;
import com.grindandtrain.core.sync.domain.DownloadLink;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.core.sync.domain.RecordChange;

/**
 * Converts between the v1 record types and the sync domain. A future API version gets its own mapper.
 *
 * @author Dheeraj_Edupuganti
 */
final class RecordMapper {

    private RecordMapper() {
    }

    static EncryptedRecord toEncryptedRecord(RecordId recordId, RecordWrite request) {
        BlobRef blob = request.getBlob();
        return new EncryptedRecord(
                recordId,
                request.getRev(),
                request.getDeleted(),
                request.getInline(),
                blob == null ? null : new BlobReference(blob.getKey(), blob.getSize(), blob.getSha256()),
                request.getWrappedDek());
    }

    static RecordPage toRecordPage(ChangePage page) {
        return new RecordPage(
                page.changes().stream().map(RecordMapper::toRecordEnvelope).toList(),
                page.nextCursor(),
                page.hasMore());
    }

    static DownloadTicket toDownloadTicket(DownloadLink link) {
        return new DownloadTicket(link.url(), link.expiresAt());
    }

    private static RecordEnvelope toRecordEnvelope(RecordChange change) {
        EncryptedRecord record = change.record();
        RecordEnvelope envelope = new RecordEnvelope(record.id().value(), record.revision(), record.deleted(), change.updatedAt());
        envelope.setInline(record.inlineCiphertext());
        envelope.setWrappedDek(record.wrappedKey());
        if (record.blob() != null) {
            envelope.setBlob(new BlobRef(record.blob().key(), record.blob().size(), record.blob().sha256()));
        }
        return envelope;
    }
}
