package com.grindandtrain.common.logging;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * The tracking id of one user action. The app may send it in {@value #HEADER}; otherwise the API creates one. It is
 * returned on the response, written on every log line, sent with every Pub/Sub event and picked up again by the
 * worker, so one action can be followed from the phone to the database.
 *
 * @author Dheeraj_Edupuganti
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    /** Letters, digits and dashes only, so a value can never break a log line or a header. */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private CorrelationId() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** The id of the action being handled on this thread, or a new one when there is none (a scheduled job). */
    public static String currentOrNew() {
        String current = MDC.get(LogFields.CORRELATION_ID);
        return current != null ? current : newId();
    }
}
