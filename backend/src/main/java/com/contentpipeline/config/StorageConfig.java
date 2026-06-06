package com.contentpipeline.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey())
        );
        var builder = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(credentials)
            .region(Region.of(props.region() != null ? props.region() : "auto"))
            .forcePathStyle(true);

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey())
        );
        var builder = S3Presigner.builder()
            .credentialsProvider(credentials)
            .region(Region.of(props.region() != null ? props.region() : "auto"));

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }
}
