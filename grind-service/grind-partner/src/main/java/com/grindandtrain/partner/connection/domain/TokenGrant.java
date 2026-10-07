package com.grindandtrain.partner.connection.domain;

import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.partner.common.error.PartnerErrorCode;

/**
 * What the app hands over to get tokens: the code from a finished OAuth sign-in, or a refresh token. Never printed:
 * {@code toString()} names the grant type only.
 *
 * @author Dheeraj_Edupuganti
 */
public sealed interface TokenGrant {

    /**
     * The code the provider returned to the app's redirect URI.
     *
     * @param codeVerifier the PKCE verifier, when the app used PKCE (Oura supports it); otherwise null
     */
    record AuthorizationCode(String code, String redirectUri, String codeVerifier) implements TokenGrant {

        public AuthorizationCode {
            if (isBlank(code) || isBlank(redirectUri)) {
                throw new GrindException(PartnerErrorCode.INVALID_CONNECTION_REQUEST);
            }
        }

        @Override
        public String toString() {
            return "AuthorizationCode[redirectUri=" + redirectUri + "]";
        }
    }

    /** A refresh token. Single-use at Oura and WHOOP: it stops working once exchanged. */
    record Refresh(String refreshToken) implements TokenGrant {

        public Refresh {
            if (isBlank(refreshToken)) {
                throw new GrindException(PartnerErrorCode.INVALID_CONNECTION_REQUEST);
            }
        }

        @Override
        public String toString() {
            return "Refresh[refreshToken=redacted]";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
