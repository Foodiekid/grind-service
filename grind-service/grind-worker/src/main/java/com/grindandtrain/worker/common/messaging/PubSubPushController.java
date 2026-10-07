package com.grindandtrain.worker.common.messaging;

import jakarta.servlet.http.HttpServletRequest;

import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;
import com.grindandtrain.common.web.RequestLoggingFilter;
import com.grindandtrain.contract.event.EventEnvelope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Pub/Sub push deliveries.
 * <p>
 * The envelope in the message attributes gives the event's type, id and the correlation id of the API request that
 * published it. Those go on every log line while the event is handled (and on the access-log line), so the worker's
 * logs join the API's. Returning 204 acknowledges the message. Any error response (a malformed envelope, an unreadable
 * payload, a failing handler) makes Pub/Sub retry it and, after the retry limit, move it to the dead-letter topic,
 * where it is kept for inspection instead of being lost.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class PubSubPushController {

    public static final String PATH = "/internal/events";

    private static final Logger log = LoggerFactory.getLogger(PubSubPushController.class);

    private final EventDispatcher eventDispatcher;

    public PubSubPushController(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(@RequestBody PubSubPushRequest request, HttpServletRequest httpRequest) {
        PubSubPushRequest.Message message = request.message();
        if (message == null || message.data() == null) {
            throw new GrindException(PlatformErrorCode.VALIDATION_FAILED);
        }
        EventEnvelope envelope = EventEnvelope.fromAttributes(message.attributes()).orElseThrow(() -> {
            log.error("Malformed or unknown event envelope");
            return new GrindException(PlatformErrorCode.VALIDATION_FAILED);
        });
        // Replaces the id CorrelationIdFilter made up (Pub/Sub can't send our header) for the rest of the request,
        // including the exception handler's line if a handler fails. That filter restores the field afterwards.
        MDC.put(LogFields.CORRELATION_ID, envelope.correlationId());
        RequestLoggingFilter.remember(httpRequest, LogFields.CORRELATION_ID, envelope.correlationId());
        try (MdcScope scope = MdcScope.open()
                .put(LogFields.EVENT_ID, envelope.eventId())
                .put(LogFields.EVENT_TYPE, envelope.eventType().wireName())) {
            long startedAt = System.nanoTime();
            eventDispatcher.dispatch(envelope, message.data());
            log.info("Handled event in {}ms", (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.noContent().build();
        }
    }
}
