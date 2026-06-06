package com.contentpipeline.pipeline.handler;

import java.util.Map;
import java.util.UUID;

/**
 * All inputs a step handler needs to execute.
 * Passed by Temporal Activities into each PipelineStepHandler.
 */
public record StepContext(
    UUID pipelineRunId,
    UUID pipelineStepRunId,
    UUID projectId,
    Map<String, UUID> inputArtifactIds,  // e.g. "script" → artifactId, "subtitles" → artifactId
    Map<String, UUID> inputAssetIds,     // e.g. "gameplay" → assetId
    Map<String, String> stepConfig,      // from PipelineStepDefinition.configJson (deserialized)
    String devUserId                     // placeholder → replaced by real userId from JWT later
) {}
