package com.contentpipeline.asset.api.dto;

import java.time.Instant;

public record DownloadUrlResponse(
    String downloadUrl,
    Instant expiresAt
) {}
