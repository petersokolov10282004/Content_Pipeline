package com.contentpipeline.project.api.dto;

import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.domain.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String name,
    String description,
    String ownerIdentifier,
    ProjectStatus status,
    int assetCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getOwnerIdentifier(),
            p.getStatus(),
            p.getAssets() != null ? p.getAssets().size() : 0,
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
