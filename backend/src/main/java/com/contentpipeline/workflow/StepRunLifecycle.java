package com.contentpipeline.workflow;

import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import io.temporal.activity.Activity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns the three step-run status transitions (RUNNING → COMPLETED | FAILED), each in its
 * <em>own</em> committed transaction.
 *
 * <p>This deliberately replaces the previous design where {@code executeStep} ran the whole
 * step inside a single {@code @Transactional} method. That held a write lock on the
 * {@code step_runs} row for the entire handler execution, which (a) collided with
 * {@link StepProgressService}'s {@code REQUIRES_NEW} phase writes on the same row, and (b) meant
 * a handler exception rolled the transaction back — discarding the {@code FAILED} status and
 * leaving the row stuck at its prior value. Committing each transition independently fixes both:
 * the row shows RUNNING while the (untransacted) handler works, progress phases persist, and a
 * terminal FAILED survives the rethrow.
 *
 * <p>It is a separate bean so the {@code @Transactional} boundaries go through the Spring proxy
 * (a self-call from {@code PipelineActivitiesImpl} would bypass them).
 */
@Component
public class StepRunLifecycle {

    private final PipelineStepRunRepository stepRunRepository;
    private final PipelineRunRepository runRepository;

    public StepRunLifecycle(
        PipelineStepRunRepository stepRunRepository,
        PipelineRunRepository runRepository
    ) {
        this.stepRunRepository = stepRunRepository;
        this.runRepository = runRepository;
    }

    /** Scalars the activity needs after this method returns (so no lazy association escapes the tx). */
    public record StepStartInfo(String configJson, UUID projectId) {}

    /**
     * Mark the step RUNNING and return the scalars the handler context needs. Reads the lazy
     * {@code stepDefinition}/{@code project} associations inside the transaction and hands back
     * plain values, so the caller never touches a detached lazy proxy.
     */
    @Transactional
    public StepStartInfo markRunningAndLoad(UUID stepRunId, UUID pipelineRunId) {
        PipelineStepRun stepRun = stepRunRepository.findById(stepRunId)
            .orElseThrow(() -> Activity.wrap(new IllegalStateException("StepRun not found: " + stepRunId)));
        PipelineRun run = runRepository.findById(pipelineRunId)
            .orElseThrow(() -> Activity.wrap(new IllegalStateException("PipelineRun not found: " + pipelineRunId)));

        stepRun.setStatus(StepRunStatus.RUNNING);
        stepRun.setStartedAt(Instant.now());
        stepRunRepository.save(stepRun);

        return new StepStartInfo(
            stepRun.getStepDefinition().getConfigJson(),
            run.getProject().getId()
        );
    }

    @Transactional
    public void markCompleted(UUID stepRunId) {
        stepRunRepository.findById(stepRunId).ifPresent(stepRun -> {
            stepRun.setStatus(StepRunStatus.COMPLETED);
            stepRun.setCompletedAt(Instant.now());
            stepRunRepository.save(stepRun);
        });
    }

    @Transactional
    public void markFailed(UUID stepRunId, String errorMessage) {
        stepRunRepository.findById(stepRunId).ifPresent(stepRun -> {
            stepRun.setStatus(StepRunStatus.FAILED);
            stepRun.setCompletedAt(Instant.now());
            stepRun.setErrorMessage(errorMessage);
            stepRunRepository.save(stepRun);
        });
    }
}
