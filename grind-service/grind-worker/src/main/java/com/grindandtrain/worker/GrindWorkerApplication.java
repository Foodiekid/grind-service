package com.grindandtrain.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Entry point of grind-worker, which receives events from Pub/Sub and applies them using the business logic in
 * grind-core. Error handling, request logging and correlation ids come from grind-common.
 *
 * @author Dheeraj_Edupuganti
 */
@SpringBootApplication(
        scanBasePackages = {"com.grindandtrain.worker", "com.grindandtrain.common", "com.grindandtrain.core"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConfigurationPropertiesScan({"com.grindandtrain.worker", "com.grindandtrain.core"})
public class GrindWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrindWorkerApplication.class, args);
    }
}
