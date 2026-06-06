package com.contentpipeline.pipeline.run.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreatePipelineRunRequest(
    @NotNull UUID pipelineTemplateId,

    /** Named asset IDs: "gameplay" → assetId. */
    Map<String, UUID> inputAssetIds,

    @NotNull InputParams inputParams,

    @Size(max = 255) String title
) {
    public record InputParams(
        @NotBlank @Size(max = 5000) String prompt,
        @Size(max = 100) String genre,
        @Size(max = 100) String tone
    ) {}
}
