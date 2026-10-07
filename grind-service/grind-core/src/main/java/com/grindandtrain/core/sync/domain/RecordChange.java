package com.grindandtrain.core.sync.domain;

import java.time.OffsetDateTime;

/**
 * A stored record together with the sequence number that orders sync-down.
 *
 * @author Dheeraj_Edupuganti
 */
public record RecordChange(EncryptedRecord record, long sequence, OffsetDateTime updatedAt) {
}
