package com.grindandtrain.worker.common.messaging;

import com.grindandtrain.contract.event.DomainEvent;
import com.grindandtrain.contract.event.EventType;

/**
 * Handles one type of event delivered by Pub/Sub. Several handlers may share a type; {@code @Order} (see
 * {@link HandlerOrder}) decides their order. The payload class comes from {@link EventType#payloadType()}, so {@code E}
 * must be that class.
 * <p>
 * Implementations must be idempotent. Throwing an exception makes Pub/Sub retry the message and, after the retry
 * limit, move it to the dead-letter topic.
 *
 * @author Dheeraj_Edupuganti
 */
public interface EventHandler<E extends DomainEvent> {

    EventType eventType();

    void handle(E event);
}
