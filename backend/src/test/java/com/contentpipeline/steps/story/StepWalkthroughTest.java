package com.contentpipeline.steps.story;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.steps.support.StepHarness;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A single, ordered run of the whole story pipeline — GENERATE_STORY → GENERATE_SUBTITLES →
 * RENDER_VIDEO → UPLOAD_VIDEO — that threads each step's real output into the next step's input
 * (mirroring {@code StoryPipelineWorkflowImpl}) and prints a clean INPUT/OUTPUT block per step so you
 * can validate the data handoff by eye.
 *
 * <p>Run it on its own to get just these four blocks on the terminal (app logging is silenced here so
 * the blocks stand out):
 * <pre>{@code  ./mvnw -q test -Dtest=StepWalkthroughTest }</pre>
 *
 * Everything is in-memory (no Temporal, DB or R2); ffmpeg is replaced by a tiny fake binary, so the
 * full run needs a POSIX shell — on non-POSIX platforms the walkthrough is skipped.
 */
class StepWalkthroughTest {

    private static StepHarness harness;
    private static Project project;
    private static Asset gameplay;
    private static GenerateStoryStepHandler generateStory;
    private static GenerateSubtitlesStepHandler generateSubtitles;
    private static RenderVideoStepHandler renderVideo;
    private static UploadVideoStepHandler uploadVideo;

    @BeforeAll
    static void setUp() throws IOException {
        silenceAppLogs(); // keep the terminal to just the INPUT/OUTPUT blocks

        harness = new StepHarness();
        project = harness.newProject();
        gameplay = harness.seedGameplayAsset(project, "raw-gameplay-bytes".getBytes(StandardCharsets.UTF_8));

        generateStory = new GenerateStoryStepHandler(harness.artifactRepository(), harness.projectRepository());
        generateSubtitles = new GenerateSubtitlesStepHandler(harness.artifactRepository(), harness.projectRepository());
        uploadVideo = new UploadVideoStepHandler(harness.artifactRepository(), harness.projectRepository());

        renderVideo = new RenderVideoStepHandler(
            harness.assetRepository(), harness.artifactRepository(), harness.storageService(),
            harness.projectRepository(), harness.storageProperties());
        Path tempDir = Files.createTempDirectory("walkthrough-renders");
        tempDir.toFile().deleteOnExit();
        ReflectionTestUtils.setField(renderVideo, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(renderVideo, "ffmpegPath", harness.fakeFfmpegWritingOutput());
    }

    @Test
    @DisplayName("Walkthrough: run all four story steps in order, printing each step's input → output")
    void walkThroughWholePipeline() throws Exception {
        assumeTrue(File.separatorChar == '/', "fake-ffmpeg shell script requires a POSIX shell");

        // The cumulative set of named artifacts, exactly as the workflow threads them between steps.
        Map<String, UUID> artifacts = new HashMap<>();

        System.out.println("\n##################  STORY PIPELINE WALKTHROUGH  ##################");

        // ── Step 1 of 4 ── GENERATE_STORY (no upstream artifacts) ───────────────
        StepContext c1 = harness.context(project)
            .config("targetDurationSeconds", "60")
            .build();
        artifacts.putAll(runStep("STEP 1/4", "GENERATE_STORY", c1, ctx -> generateStory.execute(ctx)));

        // ── Step 2 of 4 ── GENERATE_SUBTITLES (consumes "script") ───────────────
        StepContext c2 = harness.context(project)
            .artifact("script", artifacts.get("script"))
            .build();
        artifacts.putAll(runStep("STEP 2/4", "GENERATE_SUBTITLES", c2, ctx -> generateSubtitles.execute(ctx)));

        // ── Step 3 of 4 ── RENDER_VIDEO (consumes "subtitles" + "gameplay") ─────
        StepContext c3 = harness.context(project)
            .artifact("subtitles", artifacts.get("subtitles"))
            .asset("gameplay", gameplay.getId())
            .build();
        artifacts.putAll(runStep("STEP 3/4", "RENDER_VIDEO", c3, ctx -> renderVideo.execute(ctx)));

        // ── Step 4 of 4 ── UPLOAD_VIDEO (consumes "renderedVideo") ──────────────
        StepContext c4 = harness.context(project)
            .artifact("renderedVideo", artifacts.get("renderedVideo"))
            .config("privacyStatus", "PRIVATE")
            .build();
        artifacts.putAll(runStep("STEP 4/4", "UPLOAD_VIDEO", c4, ctx -> uploadVideo.execute(ctx)));

        System.out.println("##################  END OF WALKTHROUGH  ##########################\n");

        // Sanity: every step produced its handle and they chained all the way through.
        assertThat(artifacts).containsKeys("script", "subtitles", "renderedVideo", "publishResult");
    }

    /** Prints the step's INPUT, runs it, prints the OUTPUT, and returns its output handles. */
    private Map<String, UUID> runStep(String label, String handlerKey, StepContext ctx, Step step) throws Exception {
        harness.phases().clear();
        System.out.println("\n>>> " + label);
        harness.printInput(handlerKey, ctx);
        StepResult result = step.run(ctx);
        harness.printResult(result);
        return result.outputArtifactIds();
    }

    @FunctionalInterface
    private interface Step {
        StepResult run(StepContext ctx) throws Exception;
    }

    /** Silence com.contentpipeline INFO/WARN chatter so only the walkthrough blocks reach the terminal. */
    private static void silenceAppLogs() {
        org.slf4j.Logger appLogger = LoggerFactory.getLogger("com.contentpipeline");
        if (appLogger instanceof ch.qos.logback.classic.Logger logback) {
            logback.setLevel(ch.qos.logback.classic.Level.OFF);
        }
    }
}
