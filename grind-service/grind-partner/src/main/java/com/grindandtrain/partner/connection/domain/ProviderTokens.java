package com.grindandtrain.partner.connection.domain;

/**
 * Tokens issued by a provider, passed straight back to the app and never stored on the server. Never printed:
 * {@code toString()} hides both tokens.
 *
 * @param refreshToken a new refresh token, or null when the provider didn't issue one
 * @param expiresInSeconds seconds until the access token expires
 *
 * @author Dheeraj_Edupuganti
 */
public record ProviderTokens(String accessToken, String refreshToken, long expiresInSeconds, String tokenType,
        String scope) {

    @Override
    public String toString() {
        return "ProviderTokens[accessToken=redacted, refreshToken=redacted, expiresInSeconds=" + expiresInSeconds
                + ", tokenType=" + tokenType + ", scope=" + scope + "]";
    }
}
