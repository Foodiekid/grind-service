package com.grindandtrain.core.common.storage;

import java.net.URI;

import com.grindandtrain.core.common.config.CoreProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 clients configured for Cloudflare R2 (region "auto", path-style addressing).
 *
 * <p>Checksums are only calculated when an operation requires them, because R2 does not support all
 * of the SDK's newer default checksum headers. Uploads set their SHA-256 explicitly.
 *
 * @author Dheeraj_Edupuganti
 */
@Configuration
public class R2StorageConfig {

    @Bean
    S3Client s3Client(CoreProperties properties) {
        CoreProperties.Storage r2 = properties.storage();
        return S3Client.builder()
                .endpointOverride(URI.create(r2.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(r2))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(CoreProperties properties) {
        CoreProperties.Storage r2 = properties.storage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(r2.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(r2))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(CoreProperties.Storage r2) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey()));
    }
}
