package com.contentpipeline.pipeline.run.api.dto;

import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineRunResponse(
    UUID id,
    UUID projectId,
    UUID pipelineTemplateId,
    String templateName,
    PipelineRunStatus status,
    String temporalWorkflowId,
    Instant startedAt,
    Instant completedAt,
    List<StepRunResponse> steps,
    Instant createdAt,
    Instant updatedAt
) {
    public static PipelineRunResponse from(PipelineRun r) {
        return new PipelineRunResponse(
            r.getId(),
            r.getProject().getId(),
            r.getPipelineTemplate().getId(),
            r.getPipelineTemplate().getName(),
            r.getStatus(),
            r.getTemporalWorkflowId(),
            r.getStartedAt(),
            r.getCompletedAt(),
            r.getStepRuns().stream().map(StepRunResponse::from).toList(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
