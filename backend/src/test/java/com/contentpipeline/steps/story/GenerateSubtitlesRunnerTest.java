package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.ScriptArtifact;
import com.contentpipeline.artifact.domain.SubtitleArtifact;
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
 * Runs {@link GenerateSubtitlesStepHandler} ("GENERATE_SUBTITLES") by itself and shows input → output.
 *
 * This step consumes a {@code "script"} artifact (produced upstream by GENERATE_STORY) and writes a
 * {@link SubtitleArtifact}. We seed a script with {@link StepHarness#seedScript} to stand in for that
 * upstream output, then assert the returned {@code "subtitles"} handle and the computed line count.
 */
class GenerateSubtitlesRunnerTest {

    private StepHarness harness;
    private GenerateSubtitlesStepHandler handler;

    @BeforeEach
    void setUp() {
        harness = new StepHarness();
        handler = new GenerateSubtitlesStepHandler(harness.artifactRepository(), harness.projectRepository());
    }

    @Test
    @DisplayName("GENERATE_SUBTITLES: turns a seeded script into a SubtitleArtifact with a line count")
    void generatesSubtitlesFromScript() throws Exception {
        Project project = harness.newProject();
        ScriptArtifact script = harness.seedScript(project,
            "NARRATOR: Some debts can't be paid in cash.", 60);

        StepContext ctx = harness.context(project)
            .artifact("script", script.getId())
            .build();

        harness.printInput("GENERATE_SUBTITLES", ctx);
        StepResult result = handler.execute(ctx);
        harness.printResult(result);

        UUID subtitlesId = result.outputArtifactIds().get("subtitles");
        assertThat(subtitlesId).as("returns a 'subtitles' handle").isNotNull();

        SubtitleArtifact saved = (SubtitleArtifact) harness.getArtifact(subtitlesId);
        assertThat(saved.getSrtContent()).isNotBlank();
        assertThat(saved.getLineCount()).isGreaterThan(0);
        assertThat(saved.getStatus()).isEqualTo(ArtifactStatus.READY);
    }

    @Test
    @DisplayName("GENERATE_SUBTITLES: a missing 'script' input is a non-retryable StepExecutionException")
    void failsWhenScriptInputMissing() {
        Project project = harness.newProject();
        StepContext ctx = harness.context(project).build(); // no "script" wired in

        harness.printInput("GENERATE_SUBTITLES", ctx);
        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Missing required input artifact 'script'");
    }

    @Test
    @DisplayName("GENERATE_SUBTITLES: a 'script' id that does not resolve fails fast")
    void failsWhenScriptArtifactNotFound() {
        Project project = harness.newProject();
        StepContext ctx = harness.context(project)
            .artifact("script", UUID.randomUUID()) // never seeded
            .build();

        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Script artifact not found");
    }
}
