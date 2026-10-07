package com.grindandtrain.contract.event;

import java.util.Arrays;
import java.util.Optional;

import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.contract.event.sync.RecordCommittedEvent;

/**
 * Every event type, with its name on the wire and its payload class. Adding an event means adding a constant here;
 * the worker finds the payload class from the type, so publishers and handlers can't disagree about it.
 * <p>
 * Wire names are a contract with messages already in flight: never rename one.
 *
 * @author Dheeraj_Edupuganti
 */
public enum EventType {

    RECORD_COMMITTED("sync.record-committed", RecordCommittedEvent.class),
    ACCOUNT_DELETION_REQUESTED("account.deletion-requested", AccountDeletionRequestedEvent.class);

    private final String wireName;
    private final Class<? extends DomainEvent> payloadType;

    EventType(String wireName, Class<? extends DomainEvent> payloadType) {
        this.wireName = wireName;
        this.payloadType = payloadType;
    }

    /** The value of the {@link EventAttributes#EVENT_TYPE} attribute. */
    public String wireName() {
        return wireName;
    }

    public Class<? extends DomainEvent> payloadType() {
        return payloadType;
    }

    /** Empty for a type this build doesn't know, for example one added by a newer publisher. */
    public static Optional<EventType> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst();
    }
}
