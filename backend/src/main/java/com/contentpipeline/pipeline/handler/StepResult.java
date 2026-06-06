package com.contentpipeline.pipeline.handler;

import java.util.Map;
import java.util.UUID;

/**
 * The output returned by a PipelineStepHandler after execution.
 */
public record StepResult(
    boolean success,
    Map<String, UUID> outputArtifactIds,
    String errorMessage
) {
    public static StepResult success(Map<String, UUID> artifacts) {
        return new StepResult(true, artifacts, null);
    }

    public static StepResult failure(String message) {
        return new StepResult(false, Map.of(), message);
    }
}
