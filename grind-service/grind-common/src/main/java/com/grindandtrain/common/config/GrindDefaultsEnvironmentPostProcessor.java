package com.grindandtrain.common.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Loads the settings every GRIND service shares from {@value #LOCATION} (grind-common ships {@code common.yaml},
 * grind-core ships {@code core.yaml}) with the lowest precedence, so a service's own application.yaml, its profile
 * files and the deploy settings always win. Shared settings live once, in the library that owns them, instead of
 * being copied into every service.
 *
 * @author Dheeraj_Edupuganti
 */
public class GrindDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String LOCATION = "classpath*:grind/defaults/*.yaml";
    private static final String COMMON = "common.yaml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            // Earlier files win. common.yaml goes last: a library's own defaults (core.yaml) refine the generic ones.
            Arrays.sort(resources, Comparator.comparing((Resource resource) -> COMMON.equals(resource.getFilename()))
                    .thenComparing(Resource::getFilename));
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            for (Resource resource : resources) {
                loader.load("grind defaults " + resource.getFilename(), resource)
                        .forEach(source -> environment.getPropertySources().addLast(source));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + LOCATION, e);
        }
    }
}
