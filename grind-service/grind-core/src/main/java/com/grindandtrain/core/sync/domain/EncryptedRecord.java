package com.grindandtrain.core.sync.domain;

import com.grindandtrain.common.domain.RecordId;

/**
 * One revision of a user's record as the server sees it: ciphertext and the wrapped data key.
 *
 * <p>What the record contains (a workout, a journal day, and so on) is inside the ciphertext. A
 * deletion has no content. Any other revision has either inline ciphertext (up to 4 KiB) or a
 * blob uploaded to R2 beforehand, plus the wrapped key.
 *
 * @author Dheeraj_Edupuganti
 */
public record EncryptedRecord(
        RecordId id,
        long revision,
        boolean deleted,
        byte[] inlineCiphertext,
        BlobReference blob,
        String wrappedKey) {

    /** Bytes this revision occupies on the server; counts against the storage quota. */
    public long storedBytes() {
        if (blob != null) {
            return blob.size();
        }
        return inlineCiphertext != null ? inlineCiphertext.length : 0;
    }
}
