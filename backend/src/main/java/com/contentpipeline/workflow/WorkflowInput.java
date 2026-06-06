package com.contentpipeline.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable input passed to a pipeline workflow on start.
 *
 * {@code stepRunIds} is ordered by step_order — index 0 = step 1.
 * {@code inputAssets} maps role names ("gameplay") to asset IDs, mirroring the
 * run's input asset set so activities can resolve them without a DB lookup.
 */
public record WorkflowInput(
    UUID pipelineRunId,
    List<UUID> stepRunIds,
    Map<String, UUID> inputAssets
) {}
