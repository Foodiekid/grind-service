package com.grindandtrain.core.common.messaging;

import com.grindandtrain.contract.event.DomainEvent;

/**
 * Publishes events for grind-worker. Returns only once the broker has accepted the event, so a 202 response really
 * means the work is queued.
 *
 * @author Dheeraj_Edupuganti
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}
