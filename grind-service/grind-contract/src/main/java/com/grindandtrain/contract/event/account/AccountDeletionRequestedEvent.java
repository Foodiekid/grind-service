package com.grindandtrain.contract.event.account;

import java.util.UUID;

import com.grindandtrain.contract.event.DomainEvent;
import com.grindandtrain.contract.event.EventType;

/**
 * A user asked to delete their account. Every part of the worker that holds user data removes it; the sign-in user is
 * deleted last.
 *
 * @author Dheeraj_Edupuganti
 */
public record AccountDeletionRequestedEvent(UUID userId) implements DomainEvent {

    public static final int SCHEMA_VERSION = 1;

    @Override
    public EventType eventType() {
        return EventType.ACCOUNT_DELETION_REQUESTED;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }
}
