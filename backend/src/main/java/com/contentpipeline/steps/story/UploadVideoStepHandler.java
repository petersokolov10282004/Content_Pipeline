package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.PublishConfigArtifact;
import com.contentpipeline.artifact.domain.PublishResultArtifact;
import com.contentpipeline.artifact.repository.ArtifactRepository;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import com.contentpipeline.project.repository.ProjectRepository;
import com.contentpipeline.upload.domain.UploadJob;
import com.contentpipeline.upload.domain.UploadJobStatus;
import com.contentpipeline.upload.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Creates a PublishConfigArtifact from stepConfig, then queues an UploadJob.
 * Also creates a PublishResultArtifact with status MOCK (dev) or PENDING (prod)
 * so downstream steps have a stable artifact ID to reference.
 */
@Component
public class UploadVideoStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(UploadVideoStepHandler.class);

    private final ArtifactRepository artifactRepository;
    private final UploadJobRepository uploadJobRepository;
    private final PipelineStepRunRepository stepRunRepository;
    private final ProjectRepository projectRepository;

    public UploadVideoStepHandler(
        ArtifactRepository artifactRepository,
        UploadJobRepository uploadJobRepository,
        PipelineStepRunRepository stepRunRepository,
        ProjectRepository projectRepository
    ) {
        this.artifactRepository = artifactRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.stepRunRepository = stepRunRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public String handlerKey() {
        return "UPLOAD_VIDEO";
    }

    @Override
    public StepResult execute(StepContext context) throws StepExecutionException {
        UUID renderedVideoArtifactId = context.inputArtifactIds().get("renderedVideo");
        if (renderedVideoArtifactId == null) {
            throw new StepExecutionException("Missing required input artifact 'renderedVideo'", false);
        }

        var project = projectRepository.findById(context.projectId())
            .orElseThrow(() -> new StepExecutionException("Project not found: " + context.projectId(), false));

        var stepRun = stepRunRepository.findById(context.pipelineStepRunId())
            .orElseThrow(() -> new StepExecutionException(
                "StepRun not found: " + context.pipelineStepRunId(), false));

        // Build publish config from stepConfig defaults
        String privacyStatus = context.stepConfig().getOrDefault("privacyStatus", "PRIVATE");

        PublishConfigArtifact publishConfig = new PublishConfigArtifact();
        publishConfig.setProject(project);
        publishConfig.setPipelineStepRunId(context.pipelineStepRunId());
        publishConfig.setPrivacyStatus(privacyStatus);
        publishConfig.setTitle("Generated Story Video");
        publishConfig.setStatus(ArtifactStatus.READY);
        PublishConfigArtifact savedConfig = artifactRepository.save(publishConfig);

        // Queue the upload job
        UploadJob uploadJob = new UploadJob();
        uploadJob.setPipelineStepRun(stepRun);
        uploadJob.setRenderedVideoArtifactId(renderedVideoArtifactId);
        uploadJob.setPublishConfigArtifactId(savedConfig.getId());
        uploadJob.setStatus(UploadJobStatus.QUEUED);
        uploadJobRepository.save(uploadJob);

        // Create the result artifact now (PENDING) — the upload worker will flip it to READY
        PublishResultArtifact result = new PublishResultArtifact();
        result.setProject(project);
        result.setPipelineStepRunId(context.pipelineStepRunId());
        result.setPublishStatus("PENDING");
        result.setStatus(ArtifactStatus.PENDING);
        PublishResultArtifact savedResult = artifactRepository.save(result);

        log.info("Queued UploadJob for renderedVideo={}, publishResult={}, run={}",
            renderedVideoArtifactId, savedResult.getId(), context.pipelineRunId());

        return StepResult.success(Map.of("publishResult", savedResult.getId()));
    }
}
