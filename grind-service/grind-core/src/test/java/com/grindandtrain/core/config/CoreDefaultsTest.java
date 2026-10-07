package com.grindandtrain.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.grindandtrain.common.config.GrindDefaultsEnvironmentPostProcessor;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

/**
 * Checks that grind-core's defaults refine grind-common's: services with a database are ready only when it answers,
 * and the core limits are set once for every service that uses grind-core.
 *
 * @author Dheeraj_Edupuganti
 */
class CoreDefaultsTest {

    @Test
    void coreDefaultsRefineTheCommonOnes() {
        StandardEnvironment environment = new StandardEnvironment();

        new GrindDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include")).isEqualTo("readinessState, db");
        assertThat(environment.getProperty("grind.core.limits.quota-bytes")).isEqualTo("1073741824");
        assertThat(environment.getProperty("management.server.port")).isEqualTo("8081");
    }
}
