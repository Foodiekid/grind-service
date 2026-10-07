package com.grindandtrain.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Checks that the shared defaults load with the lowest precedence: present when a service says nothing, overridden
 * the moment it does.
 *
 * @author Dheeraj_Edupuganti
 */
class GrindDefaultsEnvironmentPostProcessorTest {

    @Test
    void sharedDefaultsApplyWhenTheServiceSaysNothing() {
        StandardEnvironment environment = new StandardEnvironment();

        new GrindDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.server.port")).isEqualTo("8081");
        assertThat(environment.getProperty("management.endpoint.health.probes.add-additional-paths")).isEqualTo("true");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    void aServicesOwnSettingAlwaysWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("service",
                Map.of("grind.public-api.max-request-bytes", "16384")));

        new GrindDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("grind.public-api.max-request-bytes")).isEqualTo("16384");
    }
}
