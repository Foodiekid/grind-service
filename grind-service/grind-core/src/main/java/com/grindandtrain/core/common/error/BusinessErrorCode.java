package com.grindandtrain.core.common.error;

import com.grindandtrain.common.error.ErrorCategory;
import com.grindandtrain.common.error.ErrorCode;

/**
 * Business errors of the sync service: records, blobs, keyring and account. Thrown as
 * {@link com.grindandtrain.common.error.GrindException}; the web layer turns the category into an HTTP status.
 * <p>
 * Codes are part of the public contract: add new ones, but don't rename existing ones.
 *
 * @author Dheeraj_Edupuganti
 */
public enum BusinessErrorCode implements ErrorCode {

    INVALID_RECORD(ErrorCategory.INVALID_REQUEST, "invalid_record", "Invalid record"),
    INLINE_TOO_LARGE(ErrorCategory.INVALID_REQUEST, "inline_too_large", "Inline content too large"),
    BLOB_TOO_LARGE(ErrorCategory.INVALID_REQUEST, "blob_too_large", "Blob too large"),
    INVALID_CURSOR(ErrorCategory.INVALID_REQUEST, "invalid_cursor", "Invalid cursor"),
    BLOB_NOT_OWNED(ErrorCategory.FORBIDDEN, "blob_not_owned", "Blob belongs to another user"),
    QUOTA_EXCEEDED(ErrorCategory.FORBIDDEN, "quota_exceeded", "Storage quota reached"),
    NOT_FOUND(ErrorCategory.NOT_FOUND, "not_found", "Not found"),
    STALE_REVISION(ErrorCategory.CONFLICT, "stale_revision", "A newer revision is stored"),
    KEYRING_CHANGED(ErrorCategory.CONFLICT, "keyring_changed", "The keyring changed; fetch it again"),
    ACCOUNT_DELETED(ErrorCategory.GONE, "account_deleted", "This account is being deleted"),
    BLOB_MISSING(ErrorCategory.UNPROCESSABLE, "blob_missing", "Blob not uploaded"),
    BLOB_SIZE_MISMATCH(ErrorCategory.UNPROCESSABLE, "blob_size_mismatch", "Blob size does not match"),
    EVENTS_UNAVAILABLE(ErrorCategory.UNAVAILABLE, "events_unavailable", "Try again shortly");

    private final ErrorCategory category;
    private final String code;
    private final String title;

    BusinessErrorCode(ErrorCategory category, String code, String title) {
        this.category = category;
        this.code = code;
        this.title = title;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String title() {
        return title;
    }
}
