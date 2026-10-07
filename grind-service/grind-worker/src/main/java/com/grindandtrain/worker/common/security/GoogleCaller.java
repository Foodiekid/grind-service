package com.grindandtrain.worker.common.security;

import java.util.Optional;

import com.grindandtrain.common.security.GoogleIdTokens;
import com.grindandtrain.worker.common.config.WorkerProperties;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The Google services allowed to call the worker, each identified by the service account that signed its OIDC token
 * and the audience it was issued for. Each gets an authority that opens only its own endpoint.
 *
 * @author Dheeraj_Edupuganti
 */
public enum GoogleCaller {

    /** Pub/Sub delivering events to {@code /internal/events}. */
    PUBSUB_PUSH("pubsub-push"),
    /** Cloud Scheduler starting jobs at {@code /internal/jobs/*}. */
    SCHEDULER("scheduler");

    private final String authority;

    GoogleCaller(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

    /** Which caller a verified token belongs to; empty for any other signer or audience. */
    public static Optional<GoogleCaller> identify(Jwt token, WorkerProperties properties) {
        WorkerProperties.PubSub pubSub = properties.pubsub();
        if (GoogleIdTokens.isFrom(token, pubSub.pushServiceAccount(), pubSub.pushAudience())) {
            return Optional.of(PUBSUB_PUSH);
        }
        WorkerProperties.Scheduler scheduler = properties.scheduler();
        if (GoogleIdTokens.isFrom(token, scheduler.serviceAccount(), scheduler.audience())) {
            return Optional.of(SCHEDULER);
        }
        return Optional.empty();
    }
}
