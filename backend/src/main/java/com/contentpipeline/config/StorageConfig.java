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

    // The AWS SDK rejects blank access keys at construction time, which would crash
    // startup when R2 isn't configured. We fall back to a placeholder so the beans
    // instantiate and the app boots; any actual R2 call will fail with an auth error
    // (the intended behavior until real credentials are supplied).
    private static final String PLACEHOLDER = "unconfigured";

    private static StaticCredentialsProvider credentials(StorageProperties props) {
        String accessKey = blankToPlaceholder(props.accessKeyId());
        String secretKey = blankToPlaceholder(props.secretAccessKey());
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static String blankToPlaceholder(String value) {
        return (value == null || value.isBlank()) ? PLACEHOLDER : value;
    }

    @Bean
    public S3Client s3Client(StorageProperties props) {
        var builder = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(credentials(props))
            .region(Region.of(props.region() != null ? props.region() : "auto"))
            .forcePathStyle(true);

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        // Match the S3Client: path-style addressing (bucket in the path, not a
        // subdomain). Virtual-host style would sign URLs like
        // http://<bucket>.<host>/... which don't resolve against S3-compatible
        // stores (MinIO, and R2's account endpoint) without extra DNS setup.
        var builder = S3Presigner.builder()
            .credentialsProvider(credentials(props))
            .region(Region.of(props.region() != null ? props.region() : "auto"))
            .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build());

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }
}
