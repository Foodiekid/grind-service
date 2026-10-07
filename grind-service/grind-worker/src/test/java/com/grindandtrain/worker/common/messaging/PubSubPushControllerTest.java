package com.grindandtrain.worker.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.common.web.ProblemResponseWriter;
import com.grindandtrain.contract.event.EventEnvelope;
import com.grindandtrain.contract.event.EventType;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.common.security.GoogleCaller;
import com.grindandtrain.worker.common.security.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends Pub/Sub push deliveries through security, the controller and the dispatcher: handlers run in order, a failing
 * handler or a malformed message gets an error response (so Pub/Sub retries and dead-letters it), and only Pub/Sub
 * may push.
 *
 * @author Dheeraj_Edupuganti
 */
@WebMvcTest(controllers = PubSubPushController.class)
@Import({SecurityConfig.class, EventDispatcher.class, ProblemResponseWriter.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class,
        PubSubPushControllerTest.Handlers.class})
@EnableConfigurationProperties(WorkerProperties.class)
class PubSubPushControllerTest {

    private static final List<String> CALLS = new ArrayList<>();
    private static boolean failCompletion;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void reset() {
        CALLS.clear();
        failCompletion = false;
    }

    @Test
    void runsEveryHandlerInOrderAndAcknowledges() throws Exception {
        mockMvc.perform(fromPubSub(push(attributes(EventType.ACCOUNT_DELETION_REQUESTED))))
                .andExpect(status().isNoContent());
        assertThat(CALLS).containsExactly("cleanup", "completion");
    }

    @Test
    void failingHandlerFailsTheDeliverySoPubSubRetries() throws Exception {
        failCompletion = true;
        mockMvc.perform(fromPubSub(push(attributes(EventType.ACCOUNT_DELETION_REQUESTED))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:internal"));
        assertThat(CALLS).containsExactly("cleanup");
    }

    @Test
    void unknownEventTypeIsRejectedForTheDeadLetterTopic() throws Exception {
        Map<String, String> attributes = new java.util.HashMap<>(attributes(EventType.ACCOUNT_DELETION_REQUESTED));
        attributes.put("eventType", "billing.invoice-paid");
        mockMvc.perform(fromPubSub(push(attributes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:validation_failed"));
        assertThat(CALLS).isEmpty();
    }

    @Test
    void knownTypeWithoutHandlersIsAcknowledged() throws Exception {
        mockMvc.perform(fromPubSub(push(attributes(EventType.RECORD_COMMITTED))))
                .andExpect(status().isNoContent());
    }

    @Test
    void schedulerCannotPushEvents() throws Exception {
        mockMvc.perform(push(attributes(EventType.ACCOUNT_DELETION_REQUESTED))
                        .with(jwt().authorities(new SimpleGrantedAuthority(GoogleCaller.SCHEDULER.authority()))))
                .andExpect(status().isForbidden());
        assertThat(CALLS).isEmpty();
    }

    @Test
    void pushWithoutAGoogleTokenIsUnauthorized() throws Exception {
        mockMvc.perform(push(attributes(EventType.ACCOUNT_DELETION_REQUESTED)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:unauthorized"));
        assertThat(CALLS).isEmpty();
    }

    private MockHttpServletRequestBuilder push(Map<String, String> attributes) {
        byte[] payload = jsonMapper.writeValueAsBytes(new AccountDeletionRequestedEvent(UUID.randomUUID()));
        Map<String, Object> body = Map.of(
                "subscription", "projects/test/subscriptions/grind-worker-push",
                "message", Map.of(
                        "data", Base64.getEncoder().encodeToString(payload),
                        "messageId", "1",
                        "attributes", attributes));
        return post(PubSubPushController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    private static MockHttpServletRequestBuilder fromPubSub(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority(GoogleCaller.PUBSUB_PUSH.authority())));
    }

    private static Map<String, String> attributes(EventType type) {
        return new EventEnvelope(UUID.randomUUID().toString(), type, 1, "correlation-1234", Instant.now())
                .toAttributes();
    }

    @TestConfiguration
    static class Handlers {

        @Bean
        @Order(HandlerOrder.COMPLETION)
        EventHandler<AccountDeletionRequestedEvent> completionHandler() {
            return new RecordingHandler("completion");
        }

        @Bean
        @Order(HandlerOrder.DATA_CLEANUP)
        EventHandler<AccountDeletionRequestedEvent> cleanupHandler() {
            return new RecordingHandler("cleanup");
        }
    }

    /** Records its name when called; the completion handler can be made to fail. */
    record RecordingHandler(String name) implements EventHandler<AccountDeletionRequestedEvent> {

        @Override
        public EventType eventType() {
            return EventType.ACCOUNT_DELETION_REQUESTED;
        }

        @Override
        public void handle(AccountDeletionRequestedEvent event) {
            if (failCompletion && name.equals("completion")) {
                throw new IllegalStateException("Supabase down");
            }
            CALLS.add(name);
        }
    }
}
