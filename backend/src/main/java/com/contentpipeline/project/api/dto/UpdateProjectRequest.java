package com.contentpipeline.project.api.dto;

import com.contentpipeline.project.domain.ProjectStatus;
import jakarta.validation.constraints.Size;

/**
 * All fields optional — only non-null fields are applied (partial update).
 */
public record UpdateProjectRequest(
    @Size(max = 255) String name,
    @Size(max = 10_000) String description,
    ProjectStatus status
) {}
