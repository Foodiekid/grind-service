package com.grindandtrain.partner.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.grindandtrain.common.publicapi.PublicApiProperties;
import com.grindandtrain.common.testkit.DeploySettings;
import com.grindandtrain.partner.connection.config.PartnerProperties;
import com.grindandtrain.partner.connection.domain.PartnerId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds grind-partner's real deploy settings (deploy/config/&lt;env&gt;/grind-partner.yaml) to the settings classes, so a
 * typo or a missing value fails the build instead of the next deploy, and checks that its public-API settings match
 * grind-api's exactly: both services answer the same app, so they must accept the same origins, builds and tokens.
 *
 * @author Dheeraj_Edupuganti
 */
class DeployConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void deploySettingsBindAndValidate(String env) {
        DeploySettings.runner("grind-partner", env).withUserConfiguration(Settings.class).run(context -> {
            assertThat(context).hasNotFailed();
            PartnerProperties partners = context.getBean(PartnerProperties.class);
            assertThat(partners.settings(PartnerId.of("oura"))).isPresent();
            assertThat(partners.settings(PartnerId.of("whoop"))).isPresent();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void publicApiSettingsMatchGrindApi(String env) {
        var partner = DeploySettings.properties("grind-partner", env, "grind.public-api.");
        assertThat(partner).isNotEmpty().isEqualTo(DeploySettings.properties("grind-api", env, "grind.public-api."));
    }

    @Configuration
    @EnableConfigurationProperties({PublicApiProperties.class, PartnerProperties.class})
    static class Settings {
    }
}
