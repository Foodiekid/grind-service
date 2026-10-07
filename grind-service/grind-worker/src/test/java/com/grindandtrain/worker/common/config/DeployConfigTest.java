package com.grindandtrain.worker.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.grindandtrain.common.testkit.DeploySettings;
import com.grindandtrain.core.common.config.CoreProperties;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds grind-worker's real deploy settings (deploy/config/&lt;env&gt;/grind-worker.yaml) to the settings classes, so a
 * typo or a missing value fails the build instead of the next deploy.
 *
 * @author Dheeraj_Edupuganti
 */
class DeployConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void deploySettingsBindAndValidate(String env) {
        DeploySettings.runner("grind-worker", env).withUserConfiguration(Settings.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerProperties.class).supabase().requestTimeout()).isPositive();
        });
    }

    /**
     * Dev and prod connect to the database only after checking its certificate and host name (not just encrypting),
     * with SCRAM channel binding, and take every password from Secret Manager, never from the file.
     */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void databaseConnectionVerifiesTheServerAndKeepsPasswordsInSecretManager(String env) {
        Map<String, String> spring = DeploySettings.properties("grind-worker", env, "spring.");

        assertThat(spring.get("spring.datasource.url")).contains("sslmode=verify-full", "channelBinding=require");
        assertThat(spring.get("spring.datasource.password")).isEqualTo("${DB_APP_PASSWORD}");
    }

    @Configuration
    @EnableConfigurationProperties({WorkerProperties.class, CoreProperties.class})
    static class Settings {
    }
}
