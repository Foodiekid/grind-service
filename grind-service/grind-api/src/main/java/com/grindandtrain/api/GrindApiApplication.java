package com.grindandtrain.api;

import com.grindandtrain.common.publicapi.EnableGrindPublicApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Entry point of grind-api, the public HTTP service.
 * <p>
 * Loads the shared plumbing from grind-common (including the public-API checks and security, switched on by
 * {@link EnableGrindPublicApi}) and the business logic from grind-core. Bean names are fully qualified so that classes sharing a simple
 * name across API versions ({@code sync.web.v1.RecordController}, later {@code sync.web.v2.RecordController}) can be
 * registered side by side.
 *
 * @author Dheeraj_Edupuganti
 */
@SpringBootApplication(
        scanBasePackages = {"com.grindandtrain.api", "com.grindandtrain.common", "com.grindandtrain.core"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConfigurationPropertiesScan({"com.grindandtrain.api", "com.grindandtrain.core"})
@EnableGrindPublicApi
public class GrindApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrindApiApplication.class, args);
    }
}
