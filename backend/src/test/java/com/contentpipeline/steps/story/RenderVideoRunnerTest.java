package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.RenderedVideoArtifact;
import com.contentpipeline.artifact.domain.SubtitleArtifact;
import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.steps.support.StepHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@link RenderVideoStepHandler} ("RENDER_VIDEO") by itself and shows input → output.
 *
 * Render is the one step with real I/O: it needs a {@code "subtitles"} artifact, a {@code "gameplay"}
 * asset, object storage and an ffmpeg binary. The harness supplies in-memory storage; for the binary we
 * point {@code render.ffmpeg.path} at a tiny fake script ({@link StepHarness#fakeFfmpegWritingOutput})
 * that just produces the output file — so the whole download → encode → upload → persist path runs
 * without ffmpeg installed.
 *
 * The validation guards (missing inputs, unresolved ids) need none of that and always run; the full
 * happy path is skipped on non-POSIX platforms where the fake-binary trick does not apply.
 */
class RenderVideoRunnerTest {

    private StepHarness harness;
    private RenderVideoStepHandler handler;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        harness = new StepHarness();
        handler = new RenderVideoStepHandler(
            harness.assetRepository(),
            harness.artifactRepository(),
            harness.storageService(),
            harness.projectRepository(),
            harness.storageProperties());

        tempDir = Files.createTempDirectory("harness-renders");
        tempDir.toFile().deleteOnExit();
        ReflectionTestUtils.setField(handler, "tempDir", tempDir.toString());
    }

    @Test
    @DisplayName("RENDER_VIDEO: full path — downloads gameplay, encodes, uploads, writes a RenderedVideoArtifact")
    void rendersEndToEndWithFakeFfmpeg() throws Exception {
        assumeTrue(File.separatorChar == '/', "fake-ffmpeg shell script requires a POSIX shell");
        ReflectionTestUtils.setField(handler, "ffmpegPath", harness.fakeFfmpegWritingOutput());

        Project project = harness.newProject();
        Asset gameplay = harness.seedGameplayAsset(project, "raw-gameplay-bytes".getBytes(StandardCharsets.UTF_8));
        SubtitleArtifact subtitles = harness.seedSubtitle(project,
            "1\n00:00:00,000 --> 00:00:03,000\nSome debts can't be paid in cash.\n");

        StepContext ctx = harness.context(project)
            .artifact("subtitles", subtitles.getId())
            .asset("gameplay", gameplay.getId())
            .build();

        harness.printInput("RENDER_VIDEO", ctx);
        StepResult result = handler.execute(ctx);
        harness.printResult(result);

        UUID renderedId = result.outputArtifactIds().get("renderedVideo");
        assertThat(renderedId).as("returns a 'renderedVideo' handle").isNotNull();

        RenderedVideoArtifact rendered = (RenderedVideoArtifact) harness.getArtifact(renderedId);
        assertThat(rendered.getStorageKey()).startsWith("renders/" + project.getId());
        assertThat(rendered.getStorageBucket()).isEqualTo(StepHarness.BUCKET);
        assertThat(rendered.getFileSizeBytes()).isGreaterThan(0);
        assertThat(rendered.getResolution()).isEqualTo("1080x1920");
        // The encoded output was actually uploaded to the (fake) object store.
        assertThat(harness.objectExists(rendered.getStorageBucket(), rendered.getStorageKey())).isTrue();
        // Progress was reported at each phase, in order.
        assertThat(harness.phases())
            .containsExactly("DOWNLOADING_GAMEPLAY", "RUNNING_FFMPEG", "UPLOADING_RENDER");
    }

    @Test
    @DisplayName("RENDER_VIDEO: a missing 'subtitles' artifact is a non-retryable StepExecutionException")
    void failsWhenSubtitlesMissing() {
        Project project = harness.newProject();
        Asset gameplay = harness.seedGameplayAsset(project, new byte[]{1, 2, 3});
        StepContext ctx = harness.context(project)
            .asset("gameplay", gameplay.getId())
            .build(); // no "subtitles"

        harness.printInput("RENDER_VIDEO", ctx);
        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Missing required input artifact 'subtitles'");
    }

    @Test
    @DisplayName("RENDER_VIDEO: a missing 'gameplay' asset is a non-retryable StepExecutionException")
    void failsWhenGameplayMissing() {
        Project project = harness.newProject();
        SubtitleArtifact subtitles = harness.seedSubtitle(project, "1\n00:00:00,000 --> 00:00:01,000\nHi\n");
        StepContext ctx = harness.context(project)
            .artifact("subtitles", subtitles.getId())
            .build(); // no "gameplay"

        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Missing required input asset 'gameplay'");
    }

    @Test
    @DisplayName("RENDER_VIDEO: a 'gameplay' asset id that does not resolve fails fast")
    void failsWhenGameplayAssetNotFound() {
        Project project = harness.newProject();
        SubtitleArtifact subtitles = harness.seedSubtitle(project, "1\n00:00:00,000 --> 00:00:01,000\nHi\n");
        StepContext ctx = harness.context(project)
            .artifact("subtitles", subtitles.getId())
            .asset("gameplay", UUID.randomUUID()) // never seeded
            .build();

        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Gameplay asset not found");
    }
}
