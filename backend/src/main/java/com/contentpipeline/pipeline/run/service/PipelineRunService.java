package com.contentpipeline.pipeline.run.service;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.pipeline.run.api.dto.CreatePipelineRunRequest;
import com.contentpipeline.pipeline.run.api.dto.PipelineRunResponse;
import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import com.contentpipeline.pipeline.template.repository.PipelineTemplateRepository;
import com.contentpipeline.project.service.ProjectService;
import com.contentpipeline.workflow.WorkflowInput;
import com.contentpipeline.workflow.WorkflowStarter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PipelineRunService {

    private final PipelineRunRepository runRepository;
    private final PipelineTemplateRepository templateRepository;
    private final AssetRepository assetRepository;
    private final ProjectService projectService;
    private final WorkflowStarter workflowStarter;
    private final ObjectMapper objectMapper;

    public PipelineRunService(
        PipelineRunRepository runRepository,
        PipelineTemplateRepository templateRepository,
        AssetRepository assetRepository,
        ProjectService projectService,
        WorkflowStarter workflowStarter,
        ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.templateRepository = templateRepository;
        this.assetRepository = assetRepository;
        this.projectService = projectService;
        this.workflowStarter = workflowStarter;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a PipelineRun + PipelineStepRun records for every step in the
     * template, then hands off to Temporal. The run is persisted before Temporal
     * starts so the ID always exists when the workflow queries for it.
     */
    public PipelineRunResponse create(String userId, UUID projectId, CreatePipelineRunRequest request) {
        var project = projectService.requireOwnedProject(userId, projectId);

        PipelineTemplate template = templateRepository.findById(request.pipelineTemplateId())
            .orElseThrow(() -> new ResourceNotFoundException("PipelineTemplate", request.pipelineTemplateId()));

        if (!template.getActive()) {
            throw new PipelineException("Pipeline template '" + template.getName() + "' is not active");
        }

        // Resolve and validate named input assets (e.g. "gameplay" → assetId)
        Map<String, UUID> inputAssetIdMap = new LinkedHashMap<>();
        if (request.inputAssetIds() != null) {
            for (var entry : request.inputAssetIds().entrySet()) {
                if (entry.getValue() == null) continue;
                Asset asset = assetRepository.findById(entry.getValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Asset", entry.getValue()));
                if (!asset.getProject().getId().equals(projectId)) {
                    throw new ResourceNotFoundException("Asset", entry.getValue());
                }
                inputAssetIdMap.put(entry.getKey(), asset.getId());
            }
        }

        // Persist the run
        PipelineRun run = new PipelineRun();
        run.setProject(project);
        run.setPipelineTemplate(template);
        run.setStatus(PipelineRunStatus.PENDING);
        run.setTemporalWorkflowId("story-pipeline-pending"); // overwritten after Temporal start
        run.setInputParamsJson(toJson(request.inputParams()));

        // Attach input assets to the join table
        inputAssetIdMap.values().forEach(assetId ->
            assetRepository.findById(assetId).ifPresent(run.getInputAssets()::add)
        );

        // Pre-create all step runs in PENDING so the workflow has stable IDs to pass to activities
        List<UUID> stepRunIds = new ArrayList<>();
        for (PipelineStepDefinition stepDef : template.getSteps()) {
            PipelineStepRun stepRun = new PipelineStepRun();
            stepRun.setPipelineRun(run);
            stepRun.setStepDefinition(stepDef);
            stepRun.setStepOrder(stepDef.getStepOrder());
            stepRun.setStatus(StepRunStatus.PENDING);
            run.getStepRuns().add(stepRun);
        }

        PipelineRun saved = runRepository.save(run);
        saved.getStepRuns().forEach(sr -> stepRunIds.add(sr.getId()));

        // Start Temporal workflow — the worker picks this up immediately
        WorkflowInput workflowInput = new WorkflowInput(saved.getId(), stepRunIds, inputAssetIdMap);
        String workflowId = workflowStarter.start(workflowInput);

        // Update the run with the real Temporal workflow ID
        saved.setTemporalWorkflowId(workflowId);
        saved.setStatus(PipelineRunStatus.RUNNING);

        return PipelineRunResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<PipelineRunResponse> list(String userId, UUID projectId, Pageable pageable) {
        projectService.requireOwnedProject(userId, projectId);
        return runRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
            .map(PipelineRunResponse::from);
    }

    @Transactional(readOnly = true)
    public PipelineRunResponse get(String userId, UUID projectId, UUID runId) {
        return PipelineRunResponse.from(requireOwnedRun(userId, projectId, runId));
    }

    /** Loads run and verifies project ownership. */
    @Transactional(readOnly = true)
    public PipelineRun requireOwnedRun(String userId, UUID projectId, UUID runId) {
        projectService.requireOwnedProject(userId, projectId);
        PipelineRun run = runRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("PipelineRun", runId));
        if (!run.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("PipelineRun", runId);
        }
        return run;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new PipelineException("Failed to serialize params: " + e.getMessage());
        }
    }
}
