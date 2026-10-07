package com.grindandtrain.common.publicapi;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code grind.public-api}, shared by every service that serves the public API (grind-api,
 * grind-partner): who may call, through which edge, with which app builds and tokens. Checked at start-up: a missing
 * value stops the service instead of failing on the first request.
 *
 * @param corsOrigins        origins allowed to call the API: the Capacitor apps and the Vite dev server
 * @param maxRequestBytes    largest request body accepted; blobs never go through the API
 * @param edgeSecret         shared secret Cloudflare adds to forwarded requests; empty disables the check locally
 * @param minClientVersions  oldest app build accepted per platform ({@code ios}, {@code android}, {@code web})
 * @param retiringVersions   API versions being retired, keyed by version ({@code v1})
 *
 * @author Dheeraj_Edupuganti
 */
@Validated
@ConfigurationProperties(prefix = "grind.public-api")
public record PublicApiProperties(
        List<String> corsOrigins,
        @Positive long maxRequestBytes,
        @Valid @NotNull Auth auth,
        String edgeSecret,
        Map<String, String> minClientVersions,
        Map<String, Retirement> retiringVersions) {

    public PublicApiProperties {
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
        minClientVersions = minClientVersions == null ? Map.of() : Map.copyOf(minClientVersions);
        retiringVersions = retiringVersions == null ? Map.of() : Map.copyOf(retiringVersions);
    }

    /** Where Supabase access tokens are verified: the project's signing keys and issuer. */
    public record Auth(@NotBlank String supabaseJwksUri, @NotBlank String supabaseIssuer) {
    }

    /** When a version was deprecated and when it stops working. */
    public record Retirement(LocalDate deprecatedOn, LocalDate sunsetOn) {
    }
}
