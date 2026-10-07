package com.grindandtrain.common.web;

import java.net.URI;

import com.grindandtrain.common.error.ErrorCategory;
import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.logging.CorrelationId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem details from error codes, and decides the HTTP status of each error category. This is the
 * only place that knows the mapping.
 * <p>
 * A problem has a {@code type} of {@code urn:grind:problem:<code>}, a fixed title, the status, and an {@code instance}
 * of {@code urn:grind:request:<correlation id>}, which support uses to find the request's log lines. It never has a
 * {@code detail} built from the request, and {@code instance} replaces Spring's default (the request path), so nothing
 * the caller sent is echoed back.
 *
 * @author Dheeraj_Edupuganti
 */
public final class ProblemDetailFactory {

    public static final String TYPE_PREFIX = "urn:grind:problem:";
    public static final String INSTANCE_PREFIX = "urn:grind:request:";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail of(ErrorCode error) {
        return of(statusOf(error.category()), error.code(), error.title());
    }

    public static ProblemDetail of(HttpStatus status, String code, String title) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        problem.setInstance(URI.create(currentInstance()));
        return problem;
    }

    /** This request's tracking id as a problem {@code instance}. */
    public static String currentInstance() {
        return INSTANCE_PREFIX + CorrelationId.currentOrNew();
    }

    public static HttpStatus statusOf(ErrorCategory category) {
        return switch (category) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case GONE -> HttpStatus.GONE;
            case LENGTH_REQUIRED -> HttpStatus.LENGTH_REQUIRED;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UPGRADE_REQUIRED -> HttpStatus.UPGRADE_REQUIRED;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
