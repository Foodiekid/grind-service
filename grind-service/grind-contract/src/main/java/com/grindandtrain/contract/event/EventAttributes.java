package com.grindandtrain.contract.event;

/**
 * Pub/Sub message attributes set on every event. They describe the message; the payload itself is JSON.
 *
 * @author Dheeraj_Edupuganti
 */
public final class EventAttributes {

    /** Which event this is, for example {@code sync.record-committed}. */
    public static final String EVENT_TYPE = "eventType";

    /** Layout version of the payload. */
    public static final String SCHEMA_VERSION = "schemaVersion";

    /** Unique id of this event, useful when tracing a redelivery. */
    public static final String EVENT_ID = "eventId";

    /** The tracking id of the user action that caused the event, carried from the API request to the worker. */
    public static final String CORRELATION_ID = "correlationId";

    /** When the API published the event (ISO-8601, UTC). */
    public static final String PUBLISHED_AT = "publishedAt";

    private EventAttributes() {
    }
}
