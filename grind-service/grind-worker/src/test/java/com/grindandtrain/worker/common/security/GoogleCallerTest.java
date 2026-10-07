package com.grindandtrain.worker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.grindandtrain.worker.common.config.WorkerProperties;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Checks that each Google caller is recognised only with its own service account and its own audience, so Pub/Sub
 * can't start jobs and Scheduler can't push events.
 *
 * @author Dheeraj_Edupuganti
 */
class GoogleCallerTest {

    private static final String PUSH_ACCOUNT = "pubsub-push@grind.iam.gserviceaccount.com";
    private static final String PUSH_AUDIENCE = "https://worker.example/internal/events";
    private static final String SCHEDULER_ACCOUNT = "grind-scheduler@grind.iam.gserviceaccount.com";
    private static final String SCHEDULER_AUDIENCE = "https://worker.example";

    private final WorkerProperties properties = new WorkerProperties(
            new WorkerProperties.PubSub(PUSH_AUDIENCE, PUSH_ACCOUNT),
            new WorkerProperties.Scheduler(SCHEDULER_AUDIENCE, SCHEDULER_ACCOUNT),
            new WorkerProperties.Supabase("https://p.supabase.co", "k", Duration.ofSeconds(2), Duration.ofSeconds(10)),
            new WorkerProperties.Jobs(Duration.ofMinutes(20), Duration.ofDays(14), Duration.ofHours(24)));

    @Test
    void recognisesPubSubWithItsOwnAccountAndAudience() {
        assertThat(GoogleCaller.identify(token(PUSH_ACCOUNT, PUSH_AUDIENCE, true), properties))
                .contains(GoogleCaller.PUBSUB_PUSH);
    }

    @Test
    void recognisesSchedulerWithItsOwnAccountAndAudience() {
        assertThat(GoogleCaller.identify(token(SCHEDULER_ACCOUNT, SCHEDULER_AUDIENCE, true), properties))
                .contains(GoogleCaller.SCHEDULER);
    }

    @Test
    void anAccountWithTheOtherCallersAudienceIsNobody() {
        assertThat(GoogleCaller.identify(token(SCHEDULER_ACCOUNT, PUSH_AUDIENCE, true), properties)).isEmpty();
        assertThat(GoogleCaller.identify(token(PUSH_ACCOUNT, SCHEDULER_AUDIENCE, true), properties)).isEmpty();
    }

    @Test
    void unverifiedOrUnknownAccountsAreNobody() {
        assertThat(GoogleCaller.identify(token(PUSH_ACCOUNT, PUSH_AUDIENCE, false), properties)).isEmpty();
        assertThat(GoogleCaller.identify(token("someone@example.com", PUSH_AUDIENCE, true), properties)).isEmpty();
    }

    private static Jwt token(String email, String audience, boolean verified) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("email", email)
                .claim("email_verified", verified)
                .audience(List.of(audience))
                .build();
    }
}
