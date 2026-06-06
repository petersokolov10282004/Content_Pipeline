package com.contentpipeline.asset.api.dto;

import com.contentpipeline.asset.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Step 1 of the upload flow: client declares the file it intends to upload and
 * receives a presigned PUT URL plus the {@code assetId} to confirm against later.
 */
public record CreateUploadUrlRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull AssetType assetType,
    @NotBlank @Size(max = 255) String contentType,
    @NotBlank @Size(max = 512) String originalFilename,
    @Positive Long sizeBytes
) {}
