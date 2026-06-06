package com.contentpipeline.pipeline.template.api.dto;

import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;

import java.util.List;
import java.util.UUID;

public record PipelineTemplateResponse(
    UUID id,
    String name,
    String description,
    int version,
    boolean active,
    List<StepDefinitionResponse> steps
) {
    public record StepDefinitionResponse(
        UUID id,
        int stepOrder,
        String stepHandlerKey,
        String stepName,
        String description
    ) {
        public static StepDefinitionResponse from(PipelineStepDefinition s) {
            return new StepDefinitionResponse(
                s.getId(), s.getStepOrder(), s.getStepHandlerKey(),
                s.getStepName(), s.getDescription()
            );
        }
    }

    public static PipelineTemplateResponse from(PipelineTemplate t) {
        return new PipelineTemplateResponse(
            t.getId(), t.getName(), t.getDescription(),
            t.getVersion(), t.getActive(),
            t.getSteps().stream().map(StepDefinitionResponse::from).toList()
        );
    }
}
