package com.grindandtrain.common.ops;

import com.grindandtrain.common.security.GoogleIdTokens;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Settings under {@code grind.ops}: who may read {@code /internal/status}. Operators impersonate the
 * {@code serviceAccount} (Terraform grants who may) and present a Google token issued for {@code audience}, this
 * service's own URL. Left empty (local runs), nobody may.
 *
 * @author Dheeraj_Edupuganti
 */
@ConfigurationProperties(prefix = "grind.ops")
public record OpsProperties(String serviceAccount, String audience) {

    public boolean isOperator(Jwt token) {
        return GoogleIdTokens.isFrom(token, serviceAccount, audience);
    }
}
