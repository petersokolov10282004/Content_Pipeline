package com.contentpipeline.pipeline.handler;

import com.contentpipeline.common.exception.StepExecutionException;

/**
 * Core extensibility interface for the pipeline platform.
 *
 * Every pipeline step — for any pipeline type — implements this interface.
 * Implementations are registered automatically by Spring via {@link StepHandlerRegistry}.
 *
 * To add a new step for any pipeline:
 *   1. Create a class implementing this interface, annotate with @Component
 *   2. Return a globally unique string from handlerKey()
 *   3. Seed a PipelineStepDefinition row with that key in DataInitializer
 *
 * No changes to any existing class are required.
 */
public interface PipelineStepHandler {

    /**
     * Globally unique key identifying this handler.
     * Stored in PipelineStepDefinition.stepHandlerKey.
     */
    String handlerKey();

    /**
     * Execute this pipeline step.
     *
     * Called from within a Temporal Activity. Implementations must be idempotent
     * where possible. Transient failures should throw StepExecutionException(retryable=true).
     * Terminal failures should throw StepExecutionException(retryable=false).
     *
     * @param context all inputs needed for this step
     * @return StepResult with produced artifact IDs
     * @throws StepExecutionException on failure
     */
    StepResult execute(StepContext context) throws StepExecutionException;
}
