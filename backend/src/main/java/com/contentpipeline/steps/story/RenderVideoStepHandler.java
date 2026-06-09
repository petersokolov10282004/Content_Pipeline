package com.contentpipeline.steps.story;

import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import com.contentpipeline.render.domain.RenderJob;
import com.contentpipeline.render.repository.RenderJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Creates a RenderJob record (QUEUED) and returns immediately.
 * FfmpegRenderWorker (@Scheduled) claims the job and runs FFmpeg asynchronously.
 * The step completes here — the Temporal activity does not wait for the render.
 */
@Component
public class RenderVideoStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(RenderVideoStepHandler.class);

    private final RenderJobRepository renderJobRepository;
    private final PipelineStepRunRepository stepRunRepository;

    public RenderVideoStepHandler(
        RenderJobRepository renderJobRepository,
        PipelineStepRunRepository stepRunRepository
    ) {
        this.renderJobRepository = renderJobRepository;
        this.stepRunRepository = stepRunRepository;
    }

    @Override
    public String handlerKey() {
        return "RENDER_VIDEO";
    }

    @Override
    public StepResult execute(StepContext context) throws StepExecutionException {
        UUID scriptArtifactId = context.inputArtifactIds().get("script");
        UUID subtitleArtifactId = context.inputArtifactIds().get("subtitles");
        UUID gameplayAssetId = context.inputAssetIds().get("gameplay");

        if (scriptArtifactId == null) {
            throw new StepExecutionException("Missing required input artifact 'script'", false);
        }
        if (subtitleArtifactId == null) {
            throw new StepExecutionException("Missing required input artifact 'subtitles'", false);
        }
        if (gameplayAssetId == null) {
            throw new StepExecutionException("Missing required input asset 'gameplay'", false);
        }

        var stepRun = stepRunRepository.findById(context.pipelineStepRunId())
            .orElseThrow(() -> new StepExecutionException(
                "StepRun not found: " + context.pipelineStepRunId(), false));

        RenderJob job = new RenderJob();
        job.setPipelineStepRun(stepRun);
        job.setScriptArtifactId(scriptArtifactId);
        job.setSubtitleArtifactId(subtitleArtifactId);
        job.setGameplayAssetId(gameplayAssetId);

        RenderJob saved = renderJobRepository.save(job);
        log.info("Created RenderJob {} (QUEUED) for run {}", saved.getId(), context.pipelineRunId());

        // FfmpegRenderWorker will claim this job and write the output artifact.
        // Return empty artifact map — the rendered video artifact is created by the worker.
        return StepResult.success(Map.of());
    }
}
