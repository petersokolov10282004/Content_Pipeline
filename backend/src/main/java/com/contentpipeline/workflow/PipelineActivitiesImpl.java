package com.contentpipeline.workflow;

import com.contentpipeline.common.sse.SseEmitterRegistry;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepProgress;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import com.contentpipeline.steps.registry.StepHandlerRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class PipelineActivitiesImpl implements PipelineActivities {

    private static final Logger log = LoggerFactory.getLogger(PipelineActivitiesImpl.class);

    private final PipelineRunRepository runRepository;
    private final PipelineStepRunRepository stepRunRepository;
    private final StepHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final StepProgressService stepProgressService;

    public PipelineActivitiesImpl(
        PipelineRunRepository runRepository,
        PipelineStepRunRepository stepRunRepository,
        StepHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        SseEmitterRegistry sseEmitterRegistry,
        StepProgressService stepProgressService
    ) {
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.stepProgressService = stepProgressService;
    }

    @Override
    @Transactional
    public Map<String, UUID> executeStep(
        UUID pipelineRunId,
        UUID stepRunId,
        String stepHandlerKey,
        Map<String, UUID> inputArtifacts,
        Map<String, UUID> inputAssets
    ) {
        PipelineStepRun stepRun = stepRunRepository.findById(stepRunId)
            .orElseThrow(() -> Activity.wrap(new IllegalStateException("StepRun not found: " + stepRunId)));

        PipelineRun run = runRepository.findById(pipelineRunId)
            .orElseThrow(() -> Activity.wrap(new IllegalStateException("PipelineRun not found: " + pipelineRunId)));

        stepRun.setStatus(StepRunStatus.RUNNING);
        stepRun.setStartedAt(Instant.now());
        stepRunRepository.save(stepRun);

        sseEmitterRegistry.emit(pipelineRunId, Map.of(
            "type", "STEP_STARTED",
            "step", stepHandlerKey,
            "timestamp", Instant.now().toString()
        ));

        Map<String, String> stepConfig = deserializeConfig(
            stepRun.getStepDefinition().getConfigJson());

        StepProgress progress = phase ->
            stepProgressService.report(pipelineRunId, stepRunId, stepHandlerKey, phase);

        StepContext context = new StepContext(
            pipelineRunId,
            stepRunId,
            run.getProject().getId(),
            inputArtifacts,
            inputAssets,
            stepConfig,
            "dev-user-001",
            progress
        );

        try {
            PipelineStepHandler handler = handlerRegistry.getRequired(stepHandlerKey);
            StepResult result = handler.execute(context);

            stepRun.setStatus(StepRunStatus.COMPLETED);
            stepRun.setCompletedAt(Instant.now());
            stepRunRepository.save(stepRun);

            sseEmitterRegistry.emit(pipelineRunId, Map.of(
                "type", "STEP_COMPLETED",
                "step", stepHandlerKey,
                "timestamp", Instant.now().toString()
            ));

            return result.outputArtifactIds();

        } catch (Exception e) {
            // Covers StepExecutionException, missing-handler PipelineException, and
            // any unexpected runtime failure — the step must never be left RUNNING.
            log.error("Step {} failed for run {}: {}", stepHandlerKey, pipelineRunId, e.getMessage());
            stepRun.setStatus(StepRunStatus.FAILED);
            stepRun.setCompletedAt(Instant.now());
            stepRun.setErrorMessage(e.getMessage());
            stepRunRepository.save(stepRun);

            sseEmitterRegistry.emit(pipelineRunId, Map.of(
                "type", "STEP_FAILED",
                "step", stepHandlerKey,
                "error", e.getMessage() != null ? e.getMessage() : "unknown error",
                "timestamp", Instant.now().toString()
            ));

            throw Activity.wrap(e);
        }
    }

    @Override
    @Transactional
    public void completeRun(UUID pipelineRunId, boolean success) {
        PipelineRun run = runRepository.findById(pipelineRunId)
            .orElseThrow(() -> Activity.wrap(
                new IllegalStateException("PipelineRun not found: " + pipelineRunId)));

        run.setStatus(success ? PipelineRunStatus.COMPLETED : PipelineRunStatus.STEP_FAILED);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
        log.info("PipelineRun {} marked {}", pipelineRunId, run.getStatus());

        if (success) {
            sseEmitterRegistry.emit(pipelineRunId, Map.of(
                "type", "RUN_COMPLETED",
                "timestamp", Instant.now().toString()
            ));
            sseEmitterRegistry.complete(pipelineRunId);
        } else {
            sseEmitterRegistry.emit(pipelineRunId, Map.of(
                "type", "RUN_FAILED",
                "error", "One or more steps failed",
                "timestamp", Instant.now().toString()
            ));
            sseEmitterRegistry.completeWithError(pipelineRunId,
                new RuntimeException("Pipeline run failed"));
        }
    }

    private Map<String, String> deserializeConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Could not parse stepConfig JSON, using empty config: {}", e.getMessage());
            return Map.of();
        }
    }
}
