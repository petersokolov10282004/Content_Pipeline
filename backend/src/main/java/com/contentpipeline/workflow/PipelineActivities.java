package com.contentpipeline.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.Map;
import java.util.UUID;

/**
 * Temporal activity interface for pipeline step execution.
 * Each activity wraps one PipelineStepHandler invocation with its own timeout,
 * retry policy, and heartbeat so Temporal can checkpoint long-running steps.
 */
@ActivityInterface
public interface PipelineActivities {

    /**
     * Execute a step by handler key. Returns the output artifact ID map produced
     * by the step (may be empty for steps that produce no artifacts).
     *
     * @param pipelineRunId  the run being executed
     * @param stepRunId      the specific step run record to update
     * @param stepHandlerKey the key registered in StepHandlerRegistry
     * @param inputArtifacts named artifact IDs from prior steps
     * @param inputAssets    named asset IDs from the run's input set
     */
    @ActivityMethod
    Map<String, UUID> executeStep(
        UUID pipelineRunId,
        UUID stepRunId,
        String stepHandlerKey,
        Map<String, UUID> inputArtifacts,
        Map<String, UUID> inputAssets
    );

    /** Flip the PipelineRun status to COMPLETED or STEP_FAILED when the workflow finishes. */
    @ActivityMethod
    void completeRun(UUID pipelineRunId, boolean success);
}
