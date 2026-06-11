package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.PublishConfigArtifact;
import com.contentpipeline.artifact.domain.PublishResultArtifact;
import com.contentpipeline.artifact.domain.RenderedVideoArtifact;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.steps.support.StepHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@link UploadVideoStepHandler} ("UPLOAD_VIDEO") by itself and shows input → output.
 *
 * Upload is mocked (no YouTube): given a {@code "renderedVideo"} artifact handle it writes a
 * {@link PublishConfigArtifact} (honouring {@code stepConfig.privacyStatus}) and a MOCK
 * {@link PublishResultArtifact}, returning the {@code "publishResult"} handle.
 */
class UploadVideoRunnerTest {

    private StepHarness harness;
    private UploadVideoStepHandler handler;

    @BeforeEach
    void setUp() {
        harness = new StepHarness();
        handler = new UploadVideoStepHandler(harness.artifactRepository(), harness.projectRepository());
    }

    @Test
    @DisplayName("UPLOAD_VIDEO: produces a MOCK PublishResult + a PublishConfig with the configured privacy")
    void mockPublishesRenderedVideo() throws Exception {
        Project project = harness.newProject();
        RenderedVideoArtifact rendered = harness.seedRenderedVideo(project);

        StepContext ctx = harness.context(project)
            .artifact("renderedVideo", rendered.getId())
            .config("privacyStatus", "PUBLIC")
            .build();

        harness.printInput("UPLOAD_VIDEO", ctx);
        StepResult result = handler.execute(ctx);
        harness.printResult(result);

        UUID publishResultId = result.outputArtifactIds().get("publishResult");
        assertThat(publishResultId).as("returns a 'publishResult' handle").isNotNull();

        PublishResultArtifact published = (PublishResultArtifact) harness.getArtifact(publishResultId);
        assertThat(published.getPublishStatus()).isEqualTo("MOCK");
        assertThat(published.getPlatformVideoId()).startsWith("mock-");
        assertThat(published.getPlatformUrl()).contains("youtube.com/watch?v=");
        assertThat(published.getPublishedAt()).isNotNull();

        // The PublishConfig is written but not returned in the result — verify it picked up the config.
        PublishConfigArtifact config = harness.stored(PublishConfigArtifact.class).get(0);
        assertThat(config.getPrivacyStatus()).isEqualTo("PUBLIC");

        assertThat(harness.phases()).contains("MOCK_UPLOAD");
    }

    @Test
    @DisplayName("UPLOAD_VIDEO: privacyStatus defaults to PRIVATE when not configured")
    void defaultsPrivacyToPrivate() throws Exception {
        Project project = harness.newProject();
        RenderedVideoArtifact rendered = harness.seedRenderedVideo(project);

        StepContext ctx = harness.context(project)
            .artifact("renderedVideo", rendered.getId())
            .build(); // no privacyStatus

        handler.execute(ctx);

        PublishConfigArtifact config = harness.stored(PublishConfigArtifact.class).get(0);
        assertThat(config.getPrivacyStatus()).isEqualTo("PRIVATE");
    }

    @Test
    @DisplayName("UPLOAD_VIDEO: a missing 'renderedVideo' input fails fast")
    void failsWhenRenderedVideoMissing() {
        Project project = harness.newProject();
        StepContext ctx = harness.context(project).build();

        harness.printInput("UPLOAD_VIDEO", ctx);
        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Missing required input artifact 'renderedVideo'");
    }
}
