package com.grindandtrain.worker.common.messaging;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.contract.event.DomainEvent;
import com.grindandtrain.contract.event.EventEnvelope;
import com.grindandtrain.contract.event.EventType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hands a delivered event to every handler registered for its type, in {@code @Order}.
 * <p>
 * Several modules can react to the same event; account deletion relies on this. If any handler fails, the failure is
 * logged with the handler's name and the whole delivery fails, so Pub/Sub redelivers it. Handlers are idempotent, so
 * the ones that already succeeded can safely run again.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final Map<EventType, List<EventHandler<?>>> handlersByType =
            new EnumMap<>(EventType.class);
    private final JsonMapper jsonMapper;

    public EventDispatcher(List<EventHandler<?>> handlers, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        List<EventHandler<?>> ordered = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(ordered);
        for (EventHandler<?> handler : ordered) {
            handlersByType.computeIfAbsent(handler.eventType(), type -> new ArrayList<>()).add(handler);
        }
    }

    public void dispatch(EventEnvelope envelope, byte[] payload) {
        List<EventHandler<?>> handlers = handlersByType.getOrDefault(envelope.eventType(), List.of());
        if (handlers.isEmpty()) {
            // A known type nobody handles yet: acknowledge it, otherwise Pub/Sub would redeliver it forever.
            log.warn("No handler for event type");
            return;
        }
        DomainEvent event = read(envelope, payload);
        for (EventHandler<?> handler : handlers) {
            deliver(handler, event);
        }
    }

    private DomainEvent read(EventEnvelope envelope, byte[] payload) {
        try {
            return jsonMapper.readValue(payload, envelope.eventType().payloadType());
        } catch (JacksonException e) {
            // Never log the payload. The message is retried and ends up in the dead-letter topic for inspection.
            log.error("Unreadable payload: {}", e.getClass().getSimpleName());
            throw new GrindException(PlatformErrorCode.VALIDATION_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends DomainEvent> void deliver(EventHandler<E> handler, DomainEvent event) {
        try {
            handler.handle((E) event);
        } catch (RuntimeException e) {
            log.error("Handler {} failed", handler.getClass().getSimpleName(), e);
            throw new GrindException(PlatformErrorCode.INTERNAL);
        }
    }
}
