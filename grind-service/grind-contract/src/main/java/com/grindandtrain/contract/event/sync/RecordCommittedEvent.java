package com.grindandtrain.contract.event.sync;

import java.util.UUID;

import com.grindandtrain.contract.event.DomainEvent;
import com.grindandtrain.contract.event.EventType;

/**
 * A record revision accepted by the API, waiting to be stored by the worker.
 * <p>
 * A deletion has no content. Any other revision has either {@code inlineCiphertext} or the three {@code blob*} fields,
 * plus {@code wrappedKey}.
 *
 * @author Dheeraj_Edupuganti
 */
public record RecordCommittedEvent(
        UUID userId,
        UUID recordId,
        long revision,
        boolean deleted,
        byte[] inlineCiphertext,
        String blobKey,
        Long blobSize,
        String blobSha256,
        String wrappedKey) implements DomainEvent {

    public static final int SCHEMA_VERSION = 1;

    @Override
    public EventType eventType() {
        return EventType.RECORD_COMMITTED;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }
}
