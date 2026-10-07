package com.grindandtrain.core.common.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.logging.CorrelationId;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;
import com.grindandtrain.core.common.config.CoreProperties;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.contract.event.DomainEvent;
import com.grindandtrain.contract.event.EventEnvelope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes events to Pub/Sub: the payload as JSON, the {@link EventEnvelope} as message attributes. The envelope
 * carries the current request's correlation id, so the worker continues the same trace.
 * <p>
 * Waits up to {@value #PUBLISH_TIMEOUT_SECONDS} seconds for Pub/Sub to accept the message; when it doesn't, the caller
 * gets {@code events_unavailable} (503) and the app retries.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.gcp.pubsub.enabled", matchIfMissing = true)
public class PubSubEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PubSubEventPublisher.class);
    private static final long PUBLISH_TIMEOUT_SECONDS = 10;

    private final PubSubTemplate pubSubTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String topic;

    public PubSubEventPublisher(PubSubTemplate pubSubTemplate, JsonMapper jsonMapper, Clock clock,
            CoreProperties properties) {
        this.pubSubTemplate = pubSubTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.topic = properties.messaging().topic();
    }

    @Override
    public void publish(DomainEvent event) {
        EventEnvelope envelope = EventEnvelope.of(event, UUID.randomUUID().toString(), CorrelationId.currentOrNew(),
                Instant.now(clock));
        try (MdcScope scope = MdcScope.open()
                .put(LogFields.EVENT_ID, envelope.eventId())
                .put(LogFields.EVENT_TYPE, envelope.eventType().wireName())) {
            pubSubTemplate.publish(topic, jsonMapper.writeValueAsString(event), envelope.toAttributes())
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published event");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GrindException(BusinessErrorCode.EVENTS_UNAVAILABLE, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new GrindException(BusinessErrorCode.EVENTS_UNAVAILABLE, e);
        }
    }
}
