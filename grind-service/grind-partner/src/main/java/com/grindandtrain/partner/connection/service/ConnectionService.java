package com.grindandtrain.partner.connection.service;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.partner.common.error.PartnerErrorCode;
import com.grindandtrain.partner.common.logging.PartnerAuditEvent;
import com.grindandtrain.partner.connection.client.OAuthTokenClient;
import com.grindandtrain.partner.connection.config.PartnerProperties;
import com.grindandtrain.partner.connection.config.PartnerProperties.ProviderSettings;
import com.grindandtrain.partner.connection.domain.PartnerId;
import com.grindandtrain.partner.connection.domain.ProviderTokens;
import com.grindandtrain.partner.connection.domain.TokenGrant;

import org.springframework.stereotype.Service;

/**
 * OAuth token broker for partners (Oura, WHOOP, ...), so their data reaches GRIND without the server ever seeing it.
 * <p>
 * The token request needs GRIND's client secret, which can't ship in the app, so the app sends the code (or refresh
 * token) here and gets the partner's tokens back. Nothing is stored and the partner's data endpoints are never
 * called; the app keeps the tokens encrypted, syncs them to the user's other devices as ciphertext, and fetches the
 * data itself. The service has no database: a deleted account's sign-in token stops working within the hour, and
 * nothing here outlives a request.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class ConnectionService {

    private final PartnerProperties properties;
    private final OAuthTokenClient tokenClient;
    private final AuditLogger auditLogger;

    public ConnectionService(PartnerProperties properties, OAuthTokenClient tokenClient, AuditLogger auditLogger) {
        this.properties = properties;
        this.tokenClient = tokenClient;
        this.auditLogger = auditLogger;
    }

    public ProviderTokens exchange(UserId userId, PartnerId partner, TokenGrant grant) {
        ProviderSettings settings = properties.settings(partner)
                .orElseThrow(() -> new GrindException(PartnerErrorCode.CONNECTION_NOT_CONFIGURED));
        // Only GRIND's own redirect URIs: GRIND's client secret must not redeem codes issued to anyone else.
        if (grant instanceof TokenGrant.AuthorizationCode code && !settings.redirectUris().contains(code.redirectUri())) {
            throw new GrindException(PartnerErrorCode.REDIRECT_URI_NOT_ALLOWED);
        }
        ProviderTokens tokens = tokenClient.requestTokens(settings, grant);
        if (grant instanceof TokenGrant.AuthorizationCode) {
            auditLogger.record(PartnerAuditEvent.CONNECTION_AUTHORIZED, userId, partner.value());
        }
        return tokens;
    }
}
