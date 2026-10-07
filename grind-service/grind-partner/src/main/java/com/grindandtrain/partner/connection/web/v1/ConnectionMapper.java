package com.grindandtrain.partner.connection.web.v1;

import com.grindandtrain.contract.api.v1.dto.ConnectionTokenRequest;
import com.grindandtrain.contract.api.v1.dto.ConnectionTokens;
import com.grindandtrain.partner.connection.domain.ProviderTokens;
import com.grindandtrain.partner.connection.domain.TokenGrant;

/**
 * Converts between the v1 connection types and the connection domain.
 *
 * @author Dheeraj_Edupuganti
 */
final class ConnectionMapper {

    private ConnectionMapper() {
    }

    static TokenGrant toGrant(ConnectionTokenRequest request) {
        return switch (request.getGrantType()) {
            case AUTHORIZATION_CODE ->
                    new TokenGrant.AuthorizationCode(request.getCode(), request.getRedirectUri(), request.getCodeVerifier());
            case REFRESH_TOKEN -> new TokenGrant.Refresh(request.getRefreshToken());
        };
    }

    static ConnectionTokens toConnectionTokens(ProviderTokens tokens) {
        ConnectionTokens response = new ConnectionTokens(tokens.accessToken(), tokens.expiresInSeconds(), tokens.tokenType());
        response.setRefreshToken(tokens.refreshToken());
        response.setScope(tokens.scope());
        return response;
    }
}
