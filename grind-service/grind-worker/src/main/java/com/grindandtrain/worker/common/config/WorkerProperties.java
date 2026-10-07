package com.grindandtrain.worker.common.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code grind.worker}, used only by the event consumer. Checked at start-up: a missing value stops
 * the service instead of failing on the first event.
 *
 * @author Dheeraj_Edupuganti
 */
@Validated
@ConfigurationProperties(prefix = "grind.worker")
public record WorkerProperties(
        @Valid @NotNull PubSub pubsub,
        @Valid @NotNull Scheduler scheduler,
        @Valid @NotNull Supabase supabase,
        @Valid @NotNull Jobs jobs) {

    /**
     * Who may push events to us: the token audience configured on the push subscription and the service account it
     * signs with.
     */
    public record PubSub(@NotBlank String pushAudience, @NotBlank String pushServiceAccount) {
    }

    /** Who may start scheduled jobs: Cloud Scheduler's token audience and the service account it signs with. */
    public record Scheduler(@NotBlank String audience, @NotBlank String serviceAccount) {
    }

    /**
     * Supabase admin API, used only to delete the sign-in user when an account is deleted.
     *
     * @param connectTimeout how long to wait for a connection
     * @param requestTimeout how long to wait for the whole response; a hung call fails the event, which Pub/Sub retries
     */
    public record Supabase(
            @NotBlank String url,
            @NotBlank String serviceRoleKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout) {
    }

    /**
     * Scheduled housekeeping.
     *
     * @param timeBudget          how long one run may work before it stops; the next run carries on
     * @param orphanBlobAge       uploads older than this with no record pointing to them are deleted; keep it longer
     *                            than Pub/Sub's 7-day retention, so a late commit never finds its blob gone
     * @param deletionResumeAfter an account deletion still incomplete after this long is published again
     */
    public record Jobs(
            @NotNull Duration timeBudget,
            @NotNull Duration orphanBlobAge,
            @NotNull Duration deletionResumeAfter) {
    }
}
