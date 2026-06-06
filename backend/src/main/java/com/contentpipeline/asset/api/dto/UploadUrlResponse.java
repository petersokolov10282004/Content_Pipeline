package com.contentpipeline.asset.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned from the upload-url endpoint. The client PUTs the file bytes directly
 * to {@code uploadUrl} (R2), then calls confirm-upload with {@code assetId}.
 */
public record UploadUrlResponse(
    UUID assetId,
    String uploadUrl,
    String storageKey,
    Instant expiresAt
) {}
