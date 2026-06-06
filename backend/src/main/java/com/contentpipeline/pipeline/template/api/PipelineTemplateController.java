package com.contentpipeline.pipeline.template.api;

import com.contentpipeline.pipeline.template.api.dto.PipelineTemplateResponse;
import com.contentpipeline.pipeline.template.repository.PipelineTemplateRepository;
import com.contentpipeline.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipeline-templates")
public class PipelineTemplateController {

    private final PipelineTemplateRepository templateRepository;

    public PipelineTemplateController(PipelineTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @GetMapping
    public List<PipelineTemplateResponse> list() {
        return templateRepository.findAll().stream()
            .filter(t -> t.getActive())
            .map(PipelineTemplateResponse::from)
            .toList();
    }

    @GetMapping("/{templateId}")
    public PipelineTemplateResponse get(@PathVariable UUID templateId) {
        return templateRepository.findById(templateId)
            .map(PipelineTemplateResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("PipelineTemplate", templateId));
    }
}
