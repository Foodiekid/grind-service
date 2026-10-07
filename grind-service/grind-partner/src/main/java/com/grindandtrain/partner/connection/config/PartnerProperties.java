package com.grindandtrain.partner.connection.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.grindandtrain.partner.connection.domain.PartnerId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code grind.partners}: GRIND's registered OAuth app at each partner, keyed by partner id. A partner
 * without settings simply isn't offered (the API answers {@code connection_not_configured}), so adding a partner that
 * follows standard OAuth is a settings block and a secret, with no code change. Checked at start-up.
 *
 * @author Dheeraj_Edupuganti
 */
@Validated
@ConfigurationProperties(prefix = "grind.partners")
public record PartnerProperties(Map<String, @Valid ProviderSettings> providers) {

    public PartnerProperties {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public Optional<ProviderSettings> settings(PartnerId partner) {
        return Optional.ofNullable(providers.get(partner.value()));
    }

    /**
     * One partner's OAuth app.
     *
     * @param tokenUri       the partner's token endpoint
     * @param clientSecret   from Secret Manager, never in a settings file
     * @param redirectUris   the app's redirect URIs registered at the partner; a code for any other URI is refused
     * @param connectTimeout how long to wait for a connection to the partner
     * @param requestTimeout how long to wait for the partner's answer
     */
    public record ProviderSettings(
            @NotBlank String tokenUri,
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotEmpty List<String> redirectUris,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout) {

        @Override
        public String toString() {
            return "ProviderSettings[tokenUri=" + tokenUri + ", clientId=" + clientId + ", clientSecret=redacted]";
        }
    }
}
