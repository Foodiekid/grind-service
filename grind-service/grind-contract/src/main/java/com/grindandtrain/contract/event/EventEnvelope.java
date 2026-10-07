package com.grindandtrain.contract.event;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * What travels with every event besides its payload: its id, type, payload version, the correlation id of the user
 * action that caused it, and when it was published. Sent as Pub/Sub message attributes (see {@link EventAttributes});
 * the payload itself is JSON in the message body.
 *
 * @author Dheeraj_Edupuganti
 */
public record EventEnvelope(
        String eventId,
        EventType eventType,
        int schemaVersion,
        String correlationId,
        Instant publishedAt) {

    public static EventEnvelope of(DomainEvent event, String eventId, String correlationId, Instant publishedAt) {
        return new EventEnvelope(eventId, event.eventType(), event.schemaVersion(), correlationId, publishedAt);
    }

    public Map<String, String> toAttributes() {
        return Map.of(
                EventAttributes.EVENT_ID, eventId,
                EventAttributes.EVENT_TYPE, eventType.wireName(),
                EventAttributes.SCHEMA_VERSION, String.valueOf(schemaVersion),
                EventAttributes.CORRELATION_ID, correlationId,
                EventAttributes.PUBLISHED_AT, publishedAt.toString());
    }

    /**
     * Reads the attributes of a delivered message. Empty when the type is unknown or an attribute is missing or
     * malformed: such a message can never be handled, however often it is redelivered.
     */
    public static Optional<EventEnvelope> fromAttributes(Map<String, String> attributes) {
        Optional<EventType> type = EventType.fromWireName(attributes.get(EventAttributes.EVENT_TYPE));
        String eventId = attributes.get(EventAttributes.EVENT_ID);
        String correlationId = attributes.get(EventAttributes.CORRELATION_ID);
        String schemaVersion = attributes.get(EventAttributes.SCHEMA_VERSION);
        String publishedAt = attributes.get(EventAttributes.PUBLISHED_AT);
        if (type.isEmpty() || eventId == null || correlationId == null || schemaVersion == null || publishedAt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EventEnvelope(eventId, type.get(), Integer.parseInt(schemaVersion), correlationId,
                    Instant.parse(publishedAt)));
        } catch (NumberFormatException | DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
