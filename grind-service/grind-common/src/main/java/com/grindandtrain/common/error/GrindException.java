package com.grindandtrain.common.error;

/**
 * The one exception services throw for an expected failure. It carries an {@link ErrorCode} and nothing else, so no
 * user data can leak into a response or a log line through it. The global exception handler turns it into a problem
 * response; anything else that escapes is treated as a bug.
 *
 * @author Dheeraj_Edupuganti
 */
public class GrindException extends RuntimeException {

    private final ErrorCode errorCode;

    public GrindException(ErrorCode errorCode) {
        super(errorCode.code());
        this.errorCode = errorCode;
    }

    /** Keeps the underlying failure for the logs, never for the response. */
    public GrindException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.code(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
