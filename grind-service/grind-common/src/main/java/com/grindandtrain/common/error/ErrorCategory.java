package com.grindandtrain.common.error;

/**
 * What kind of failure an {@link ErrorCode} is, independent of transport. The web layer turns each category into one
 * HTTP status (see {@code ProblemDetailFactory}); services never deal with status codes.
 *
 * @author Dheeraj_Edupuganti
 */
public enum ErrorCategory {

    /** The request is malformed or breaks a rule. Retrying it unchanged fails again. */
    INVALID_REQUEST,
    /** No valid credentials. */
    UNAUTHORIZED,
    /** Valid credentials, but not allowed. */
    FORBIDDEN,
    NOT_FOUND,
    /** Someone changed the data meanwhile; fetch it again before retrying. */
    CONFLICT,
    /** Gone for good, for example a deleted account. */
    GONE,
    /** The body must declare its length. */
    LENGTH_REQUIRED,
    /** The body is larger than allowed. */
    TOO_LARGE,
    /** Well-formed, but refers to something in the wrong state (a blob not uploaded yet). */
    UNPROCESSABLE,
    /** The app build is too old and must be updated. */
    UPGRADE_REQUIRED,
    /** A dependency is down for a moment; retrying later can succeed. */
    UNAVAILABLE,
    /** A bug on our side. */
    INTERNAL
}
