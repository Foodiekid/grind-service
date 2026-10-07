package com.grindandtrain.core.common.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code grind.core}, used by both services: object storage, limits and messaging. Checked at start-up:
 * a missing value stops the service instead of failing on the first request.
 *
 * @author Dheeraj_Edupuganti
 */
@Validated
@ConfigurationProperties(prefix = "grind.core")
public record CoreProperties(@Valid @NotNull Storage storage, @Valid @NotNull Limits limits,
        @Valid @NotNull Messaging messaging) {

    /** Cloudflare R2, reached through its S3 API. Upload and download URLs expire after {@code urlTtl}. */
    public record Storage(
            @NotBlank String endpoint,
            @NotBlank String bucket,
            @NotBlank String accessKeyId,
            @NotBlank String secretAccessKey,
            @NotNull Duration urlTtl) {
    }

    /** Size limits for stored data and the storage quota per user. */
    public record Limits(@Positive int maxInlineBytes, @Positive long maxBlobBytes, @Positive long quotaBytes) {
    }

    /** Pub/Sub topic events are published to. */
    public record Messaging(@NotBlank String topic) {
    }
}
