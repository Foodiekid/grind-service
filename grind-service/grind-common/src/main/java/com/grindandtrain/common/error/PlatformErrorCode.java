package com.grindandtrain.common.error;

/**
 * Errors that don't belong to a business area: authentication, request headers and size, validation, outages and
 * bugs. Business errors live in grind-core.
 *
 * @author Dheeraj_Edupuganti
 */
public enum PlatformErrorCode implements ErrorCode {

    VALIDATION_FAILED(ErrorCategory.INVALID_REQUEST, "validation_failed", "The request doesn't match the contract"),
    CLIENT_HEADER(ErrorCategory.INVALID_REQUEST, "client_header", "Missing or invalid X-Grind-Client"),
    CORRELATION_ID(ErrorCategory.INVALID_REQUEST, "correlation_id", "Invalid X-Correlation-Id"),
    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED, "unauthorized", "Missing or invalid token"),
    // Also used when a request bypassed the edge: a caller must not learn that the edge secret exists.
    FORBIDDEN(ErrorCategory.FORBIDDEN, "forbidden", "Forbidden"),
    LENGTH_REQUIRED(ErrorCategory.LENGTH_REQUIRED, "length_required", "Content-Length required"),
    REQUEST_TOO_LARGE(ErrorCategory.TOO_LARGE, "too_large", "Request too large"),
    UPGRADE_REQUIRED(ErrorCategory.UPGRADE_REQUIRED, "upgrade_required", "Please update the app"),
    UNAVAILABLE(ErrorCategory.UNAVAILABLE, "unavailable", "Try again shortly"),
    INTERNAL(ErrorCategory.INTERNAL, "internal", "Something went wrong");

    private final ErrorCategory category;
    private final String code;
    private final String title;

    PlatformErrorCode(ErrorCategory category, String code, String title) {
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
