package com.grindandtrain.common.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grindandtrain.common.config.GrindDefaultsEnvironmentPostProcessor;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

/**
 * Loads a service's real settings the way Cloud Run will see them, for tests that must fail the build (not the
 * deploy) on a typo or a missing value: the deploy file {@code deploy/config/<env>/<service>.yaml} on top of the jar's
 * own {@code config/application.yaml} and the shared library defaults ({@code grind/defaults/*.yaml}, loaded by the
 * same post-processor as a real start), with every {@code ${NAME}} secret placeholder filled with a dummy value, as
 * Secret Manager would.
 *
 * @author Dheeraj_Edupuganti
 */
public final class DeploySettings {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)}");

    private DeploySettings() {
    }

    /** A context runner with the settings loaded and validation on; add the settings classes to bind. */
    public static ApplicationContextRunner runner(String service, String env) {
        List<PropertySource<?>> deploy = load(deployFile(service, env));
        List<PropertySource<?>> jarDefaults = loadJarDefaults();
        Map<String, Object> secrets = secretPlaceholders(deployFile(service, env));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    deploy.forEach(sources::addFirst);
                    jarDefaults.forEach(sources::addLast);
                    sources.addLast(new MapPropertySource("secrets", secrets));
                    new GrindDefaultsEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), null);
                });
    }

    /** The deploy file's settings under a prefix, for comparing services. */
    public static Map<String, String> properties(String service, String env, String prefix) {
        Map<String, String> settings = new TreeMap<>();
        for (PropertySource<?> source : load(deployFile(service, env))) {
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (name.startsWith(prefix)) {
                    settings.put(name, String.valueOf(source.getProperty(name)));
                }
            }
        }
        return settings;
    }

    private static Path deployFile(String service, String env) {
        // Tests run from the module folder (grind-service/<module>).
        return Path.of("..", "deploy", "config", env, service + ".yaml");
    }

    private static List<PropertySource<?>> load(Path file) {
        try {
            return new YamlPropertySourceLoader().load(file.toString(), new FileSystemResource(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<PropertySource<?>> loadJarDefaults() {
        try {
            return new YamlPropertySourceLoader().load("jar", new ClassPathResource("config/application.yaml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> secretPlaceholders(Path file) {
        try {
            Matcher matcher = PLACEHOLDER.matcher(Files.readString(file, StandardCharsets.UTF_8));
            Map<String, Object> secrets = new LinkedHashMap<>();
            while (matcher.find()) {
                secrets.put(matcher.group(1), "test-secret");
            }
            return secrets;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
