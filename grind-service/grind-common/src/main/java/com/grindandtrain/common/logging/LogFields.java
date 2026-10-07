package com.grindandtrain.common.logging;

/**
 * Names of the fields a log line can carry besides its message (SLF4J MDC / Log4j2 thread context). Locally they show
 * up in the text pattern; on Cloud Run they become Cloud Logging labels, so a search like
 * {@code labels.correlationId="..."} finds every line of one user action across both services.
 * <p>
 * Ids belong in these fields, not in the message: the message is masked (see {@link SensitiveDataRewritePolicy}),
 * fields are not.
 *
 * @author Dheeraj_Edupuganti
 */
public final class LogFields {

    /** Tracking id of one user action, from the app through the API and Pub/Sub to the worker. */
    public static final String CORRELATION_ID = "correlationId";

    /** Pub/Sub event being published or handled. */
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";

    /** Scheduled job being run, for example {@code orphan-blob-sweep}. */
    public static final String JOB = "job";

    /** App build that sent the request, for example {@code ios/1.4.2}. */
    public static final String CLIENT = "client";

    /** Pseudonym of the signed-in user, never the real id. */
    public static final String USER = "user";

    private LogFields() {
    }
}
