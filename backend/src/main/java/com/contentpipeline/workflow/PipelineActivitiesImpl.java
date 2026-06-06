package com.contentpipeline.workflow;

import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import com.contentpipeline.steps.registry.StepHandlerRegistry;
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

    public PipelineActivitiesImpl(
        PipelineRunRepository runRepository,
        PipelineStepRunRepository stepRunRepository,
        StepHandlerRegistry handlerRegistry
    ) {
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
        this.handlerRegistry = handlerRegistry;
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

        StepContext context = new StepContext(
            pipelineRunId,
            stepRunId,
            run.getProject().getId(),
            inputArtifacts,
            inputAssets,
            Map.of(),
            "dev-user-001"
        );

        try {
            PipelineStepHandler handler = handlerRegistry.getRequired(stepHandlerKey);
            StepResult result = handler.execute(context);

            stepRun.setStatus(StepRunStatus.COMPLETED);
            stepRun.setCompletedAt(Instant.now());
            stepRunRepository.save(stepRun);

            return result.outputArtifactIds();

        } catch (Exception e) {
            // Covers StepExecutionException, missing-handler PipelineException, and
            // any unexpected runtime failure — the step must never be left RUNNING.
            log.error("Step {} failed for run {}: {}", stepHandlerKey, pipelineRunId, e.getMessage());
            stepRun.setStatus(StepRunStatus.FAILED);
            stepRun.setCompletedAt(Instant.now());
            stepRun.setErrorMessage(e.getMessage());
            stepRunRepository.save(stepRun);
            throw Activity.wrap(e);
        }
    }
}
