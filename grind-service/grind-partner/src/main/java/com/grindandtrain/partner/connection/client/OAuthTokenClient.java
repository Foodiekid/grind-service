package com.grindandtrain.partner.connection.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.http.HttpRequestFactories;
import com.grindandtrain.partner.common.error.PartnerErrorCode;
import com.grindandtrain.partner.connection.config.PartnerProperties.ProviderSettings;
import com.grindandtrain.partner.connection.domain.ProviderTokens;
import com.grindandtrain.partner.connection.domain.TokenGrant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls a provider's OAuth token endpoint (RFC 6749 section 4.1.3 and 6): a form POST with GRIND's client id and
 * secret in the body, as both Oura and WHOOP accept.
 * <p>
 * Nothing here is logged except the outcome: never the grant, the tokens or the provider's response body. A provider
 * that refuses the grant becomes {@code connection_rejected}; a slow or failing provider, or one that refuses GRIND's
 * own credentials, becomes {@code provider_unavailable}.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class OAuthTokenClient {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenClient.class);

    private final RestClient.Builder restClientBuilder;
    /** One client (and connection pool) per provider, built on first use. */
    private final Map<ProviderSettings, RestClient> restClients = new ConcurrentHashMap<>();

    public OAuthTokenClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public ProviderTokens requestTokens(ProviderSettings settings, TokenGrant grant) {
        TokenResponse response;
        try {
            response = restClientFor(settings).post()
                    .uri(settings.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form(settings, grant))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value()
                            || status.value() == HttpStatus.FORBIDDEN.value(), (request, reply) -> {
                                // The provider refused GRIND's client id or secret: a configuration problem, not the user's.
                                log.error("Provider refused GRIND's client credentials (HTTP {})", reply.getStatusCode().value());
                                throw new GrindException(PartnerErrorCode.PROVIDER_UNAVAILABLE);
                            })
                    .onStatus(status -> status.is4xxClientError(), (request, reply) -> {
                        throw new GrindException(PartnerErrorCode.CONNECTION_REJECTED);
                    })
                    .onStatus(status -> status.is5xxServerError(), (request, reply) -> {
                        throw new GrindException(PartnerErrorCode.PROVIDER_UNAVAILABLE);
                    })
                    .body(TokenResponse.class);
        } catch (ResourceAccessException e) {
            throw new GrindException(PartnerErrorCode.PROVIDER_UNAVAILABLE, e);
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            log.error("Provider answered without an access token");
            throw new GrindException(PartnerErrorCode.PROVIDER_UNAVAILABLE);
        }
        return new ProviderTokens(response.accessToken(), response.refreshToken(),
                response.expiresIn() == null ? 0 : response.expiresIn(), response.tokenType(), response.scope());
    }

    private RestClient restClientFor(ProviderSettings settings) {
        return restClients.computeIfAbsent(settings, this::newRestClient);
    }

    private RestClient newRestClient(ProviderSettings settings) {
        return restClientBuilder.clone()
                .requestFactory(HttpRequestFactories.withTimeouts(settings.connectTimeout(), settings.requestTimeout()))
                .build();
    }

    private static MultiValueMap<String, String> form(ProviderSettings settings, TokenGrant grant) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        switch (grant) {
            case TokenGrant.AuthorizationCode code -> {
                form.add("grant_type", "authorization_code");
                form.add("code", code.code());
                form.add("redirect_uri", code.redirectUri());
                if (code.codeVerifier() != null) {
                    form.add("code_verifier", code.codeVerifier());
                }
            }
            case TokenGrant.Refresh refresh -> {
                form.add("grant_type", "refresh_token");
                form.add("refresh_token", refresh.refreshToken());
            }
        }
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        return form;
    }

    /** The standard OAuth token response (RFC 6749 section 5.1). */
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope) {

        @Override
        public String toString() {
            return "TokenResponse[redacted]";
        }
    }
}
