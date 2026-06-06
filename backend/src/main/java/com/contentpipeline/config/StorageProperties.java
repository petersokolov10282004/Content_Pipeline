package com.contentpipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.r2")
public record StorageProperties(
    String accountId,
    String accessKeyId,
    String secretAccessKey,
    String bucket,
    String endpoint,
    String region
) {}
