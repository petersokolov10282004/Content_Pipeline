package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.PublishConfigArtifact;
import com.contentpipeline.artifact.domain.PublishResultArtifact;
import com.contentpipeline.artifact.repository.ArtifactRepository;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class UploadVideoStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(UploadVideoStepHandler.class);

    private final ArtifactRepository artifactRepository;
    private final ProjectRepository projectRepository;

    public UploadVideoStepHandler(
        ArtifactRepository artifactRepository,
        ProjectRepository projectRepository
    ) {
        this.artifactRepository = artifactRepository;
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

        String privacyStatus = context.stepConfig().getOrDefault("privacyStatus", "PRIVATE");

        PublishConfigArtifact publishConfig = new PublishConfigArtifact();
        publishConfig.setProject(project);
        publishConfig.setPipelineStepRunId(context.pipelineStepRunId());
        publishConfig.setPrivacyStatus(privacyStatus);
        publishConfig.setTitle("Generated Story Video");
        publishConfig.setStatus(ArtifactStatus.READY);
        artifactRepository.save(publishConfig);

        context.progress().report("MOCK_UPLOAD");

        String mockVideoId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        PublishResultArtifact result = new PublishResultArtifact();
        result.setProject(project);
        result.setPipelineStepRunId(context.pipelineStepRunId());
        result.setPublishStatus("MOCK");
        result.setPlatformVideoId(mockVideoId);
        result.setPlatformUrl("https://youtube.com/watch?v=" + mockVideoId);
        result.setPublishedAt(Instant.now());
        result.setStatus(ArtifactStatus.READY);
        PublishResultArtifact saved = artifactRepository.save(result);

        log.info("Mock YouTube upload complete: videoId={}, publishResult={}, run={}",
            mockVideoId, saved.getId(), context.pipelineRunId());
        return StepResult.success(Map.of("publishResult", saved.getId()));
    }
}
