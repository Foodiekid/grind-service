package com.grindandtrain.common.security;

import java.util.Collection;
import java.util.function.Predicate;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Google-signed OIDC tokens from service accounts: how Google services (Pub/Sub, Cloud Scheduler) and operators call
 * internal endpoints. A token counts only when Google signed it and it was issued to an expected account for an
 * expected audience.
 *
 * @author Dheeraj_Edupuganti
 */
public final class GoogleIdTokens {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    private GoogleIdTokens() {
    }

    /** A decoder that accepts only Google-signed tokens that {@code accepted} recognises. */
    public static JwtDecoder decoder(Predicate<Jwt> accepted) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> knownCaller = token -> accepted.test(token)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unknown caller", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), knownCaller));
        return decoder;
    }

    /** Whether the token was issued to this verified account for this audience. */
    public static boolean isFrom(Jwt token, String serviceAccount, String audience) {
        if (serviceAccount == null || serviceAccount.isBlank() || audience == null || audience.isBlank()) {
            return false;
        }
        Collection<String> tokenAudience = token.getAudience();
        return Boolean.TRUE.equals(token.getClaim("email_verified"))
                && serviceAccount.equals(token.getClaimAsString("email"))
                && tokenAudience != null && tokenAudience.contains(audience);
    }
}
