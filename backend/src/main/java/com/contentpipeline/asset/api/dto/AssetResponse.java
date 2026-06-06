package com.contentpipeline.asset.api.dto;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.domain.AssetStatus;
import com.contentpipeline.asset.domain.AssetType;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
    UUID id,
    UUID projectId,
    String name,
    AssetType assetType,
    String contentType,
    String originalFilename,
    Long sizeBytes,
    AssetStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static AssetResponse from(Asset a) {
        return new AssetResponse(
            a.getId(),
            a.getProject().getId(),
            a.getName(),
            a.getAssetType(),
            a.getContentType(),
            a.getOriginalFilename(),
            a.getSizeBytes(),
            a.getStatus(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }
}
