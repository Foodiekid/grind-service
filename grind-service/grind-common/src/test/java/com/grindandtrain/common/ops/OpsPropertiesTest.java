package com.grindandtrain.common.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Checks who counts as an operator for {@code /internal/status}: only the configured account, verified, for this
 * service's own URL; and nobody when the settings are empty (local runs).
 *
 * @author Dheeraj_Edupuganti
 */
class OpsPropertiesTest {

    private static final String OPS = "grind-ops@grind-prod.iam.gserviceaccount.com";
    private static final String URL = "https://grind-api-abc.run.app";

    private final OpsProperties ops = new OpsProperties(OPS, URL);

    @Test
    void theOperatorAccountForThisServiceIsAnOperator() {
        assertThat(ops.isOperator(token(OPS, URL, true))).isTrue();
    }

    @Test
    void anyOtherAccountAudienceOrUnverifiedTokenIsNot() {
        assertThat(ops.isOperator(token("someone@example.com", URL, true))).isFalse();
        assertThat(ops.isOperator(token(OPS, "https://grind-worker-abc.run.app", true))).isFalse();
        assertThat(ops.isOperator(token(OPS, URL, false))).isFalse();
    }

    @Test
    void emptySettingsLetNobodyIn() {
        assertThat(new OpsProperties(null, null).isOperator(token(OPS, URL, true))).isFalse();
        assertThat(new OpsProperties("", "").isOperator(token("", "", true))).isFalse();
    }

    private static Jwt token(String email, String audience, boolean verified) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("email", email).claim("email_verified", verified).audience(List.of(audience)).build();
    }
}
