package com.grindandtrain.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The log line dev and prod write, rendered the way Cloud Run will see it: masking first, then Google Cloud's JSON
 * template. Error Reporting groups failures only when an ERROR entry carries its stack trace in {@code message}, and
 * support finds a user's requests by the correlation id and pseudonym labels, so those are checked here instead of
 * on the first incident.
 *
 * @author Dheeraj_Edupuganti
 */
class CloudLogFormatTest {

    private static final JsonTemplateLayout CLOUD = JsonTemplateLayout.newBuilder()
            .setConfiguration(new DefaultConfiguration())
            .setEventTemplateUri("classpath:GcpLayout.json")
            .build();

    @Test
    void errorEntryCarriesStackTraceLabelsAndNoPersonalData() throws IOException {
        RuntimeException failure = new RuntimeException("request from jo.doe@example.com",
                new IllegalStateException("row for 3f2a8c1e-9b4d-4e7a-8f6c-2d1e0b9a7c5f failed"));

        JsonNode entry = render(event(Level.ERROR, "Unhandled exception", failure));

        assertThat(entry.get("severity").asString()).isEqualTo("ERROR");
        String message = entry.get("message").asString();
        assertThat(message)
                .contains("Unhandled exception")
                .contains("java.lang.RuntimeException: request from [email]")
                .contains("Caused by: java.lang.IllegalStateException: row for [uuid] failed")
                .contains("\tat com.grindandtrain.common.logging.CloudLogFormatTest")
                .doesNotContain("jo.doe@example.com", "3f2a8c1e-9b4d-4e7a-8f6c-2d1e0b9a7c5f");
        JsonNode labels = entry.get("logging.googleapis.com/labels");
        assertThat(labels.get("correlationId").asString()).isEqualTo("4b1f0c2e-7d3a-4f5b-9c8e-1a2b3c4d5e6f");
        assertThat(labels.get("user").asString()).isEqualTo("user:3256b2877f9a0efa");
    }

    @Test
    void warningsUseCloudSeverityNames() throws IOException {
        JsonNode entry = render(event(Level.WARN, "Database unavailable", null));

        assertThat(entry.get("severity").asString()).isEqualTo("WARNING");
        assertThat(entry.get("message").asString()).isEqualTo("Database unavailable");
    }

    @Test
    void devAndProdLogThroughMaskingIntoTheCloudTemplate() throws IOException {
        String config;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("log4j2-spring.xml")) {
            assertThat(in).isNotNull();
            config = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String cloudProfile = config.substring(config.indexOf("<SpringProfile name=\"dev | prod\">"),
                config.indexOf("</SpringProfile>"));
        assertThat(cloudProfile).contains("eventTemplateUri=\"classpath:GcpLayout.json\"");
        assertThat(config).contains("<SensitiveDataRewritePolicy/>");
        assertThat(config.substring(config.indexOf("<Root"))).contains("<AppenderRef ref=\"Masked\"/>");
    }

    private static LogEvent event(Level level, String message, Throwable thrown) {
        StringMap labels = ContextDataFactory.createContextData();
        labels.putValue("correlationId", "4b1f0c2e-7d3a-4f5b-9c8e-1a2b3c4d5e6f");
        labels.putValue("user", "user:3256b2877f9a0efa");
        LogEvent raw = Log4jLogEvent.newBuilder()
                .setLoggerName("com.grindandtrain.common.web.GlobalExceptionHandler")
                .setLevel(level)
                .setMessage(new SimpleMessage(message))
                .setThrown(thrown)
                .setContextData(labels)
                .build();
        return SensitiveDataRewritePolicy.create().rewrite(raw);
    }

    private static JsonNode render(LogEvent event) {
        return JsonMapper.builder().build().readTree(CLOUD.toSerializable(event));
    }
}
