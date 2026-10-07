package com.grindandtrain.partner;

import com.grindandtrain.common.publicapi.EnableGrindPublicApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Entry point of grind-partner, the service that connects GRIND to partner platforms (Oura, WHOOP, ...).
 * <p>
 * Stateless and without a database: it brokers OAuth tokens and stores nothing. It serves part of the public API
 * (Cloudflare routes {@code /grind/api/v1/connections/**} here) with the same checks as grind-api, from grind-common.
 * Partner client secrets live only in this service.
 *
 * @author Dheeraj_Edupuganti
 */
@SpringBootApplication(
        scanBasePackages = {"com.grindandtrain.partner", "com.grindandtrain.common"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConfigurationPropertiesScan("com.grindandtrain.partner")
@EnableGrindPublicApi
public class GrindPartnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrindPartnerApplication.class, args);
    }
}
