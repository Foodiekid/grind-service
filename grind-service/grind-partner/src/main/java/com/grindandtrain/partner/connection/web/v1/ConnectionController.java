package com.grindandtrain.partner.connection.web.v1;

import com.grindandtrain.common.publicapi.CurrentUser;
import com.grindandtrain.contract.api.v1.ConnectionsApi;
import com.grindandtrain.contract.api.v1.dto.ConnectionTokenRequest;
import com.grindandtrain.contract.api.v1.dto.ConnectionTokens;
import com.grindandtrain.partner.connection.domain.PartnerId;
import com.grindandtrain.partner.connection.service.ConnectionService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connection endpoints of API v1: the OAuth token broker for partners. Token responses are never cached
 * (RFC 6749 section 5.1).
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class ConnectionController implements ConnectionsApi {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Override
    public ResponseEntity<ConnectionTokens> exchangeConnectionToken(String provider,
            ConnectionTokenRequest connectionTokenRequest) {
        var tokens = connectionService.exchange(CurrentUser.id(), PartnerId.of(provider),
                ConnectionMapper.toGrant(connectionTokenRequest));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ConnectionMapper.toConnectionTokens(tokens));
    }
}
