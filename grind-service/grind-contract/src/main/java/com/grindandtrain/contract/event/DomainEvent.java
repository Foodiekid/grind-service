package com.grindandtrain.contract.event;

/**
 * An event sent from grind-api to grind-worker through Pub/Sub.
 * <p>
 * Events carry only what the server is allowed to store anyway: ids, revision numbers, sizes and ciphertext. Never
 * health data. Delivery is at least once, so consumers must handle the same event more than once safely.
 * <p>
 * A payload change that old consumers can't read gets a new {@link #schemaVersion()}; the event type stays the same.
 *
 * @author Dheeraj_Edupuganti
 */
public interface DomainEvent {

    EventType eventType();

    /** Version of the payload layout, sent as the {@link EventAttributes#SCHEMA_VERSION} attribute. */
    int schemaVersion();
}
