package com.contentpipeline.pipeline.run.api.dto;

import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;

import java.time.Instant;
import java.util.UUID;

public record StepRunResponse(
    UUID id,
    int stepOrder,
    String stepHandlerKey,
    String stepName,
    StepRunStatus status,
    Instant startedAt,
    Instant completedAt,
    int attemptNumber,
    String errorMessage
) {
    public static StepRunResponse from(PipelineStepRun s) {
        return new StepRunResponse(
            s.getId(),
            s.getStepOrder(),
            s.getStepDefinition().getStepHandlerKey(),
            s.getStepDefinition().getStepName(),
            s.getStatus(),
            s.getStartedAt(),
            s.getCompletedAt(),
            s.getAttemptNumber(),
            s.getErrorMessage()
        );
    }
}
