package com.grindandtrain.worker.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Checks that the worker refuses to start when a required setting is missing, instead of failing on the first event.
 *
 * @author Dheeraj_Edupuganti
 */
class WorkerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Settings.class)
            .withPropertyValues(
                    "grind.worker.pubsub.push-audience=https://worker.example/internal/events",
                    "grind.worker.pubsub.push-service-account=push@example.iam.gserviceaccount.com",
                    "grind.worker.scheduler.audience=https://worker.example",
                    "grind.worker.scheduler.service-account=scheduler@example.iam.gserviceaccount.com",
                    "grind.worker.jobs.time-budget=20m",
                    "grind.worker.jobs.orphan-blob-age=14d",
                    "grind.worker.jobs.deletion-resume-after=24h",
                    "grind.worker.supabase.url=https://project.supabase.co",
                    "grind.worker.supabase.service-role-key=key",
                    "grind.worker.supabase.connect-timeout=2s",
                    "grind.worker.supabase.request-timeout=10s");

    @Test
    void completeSettingsBind() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerProperties.class).supabase().requestTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void missingSettingStopsStartUp() {
        contextRunner.withPropertyValues("grind.worker.supabase.url=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("supabase.url"));
    }

    @Configuration
    @EnableConfigurationProperties(WorkerProperties.class)
    static class Settings {
    }
}
