package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.ScriptArtifact;
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
 * Runs {@link GenerateStoryStepHandler} ("GENERATE_STORY") by itself and shows its input → output.
 *
 * This step takes no upstream artifacts — only {@code stepConfig.targetDurationSeconds} — and writes
 * a {@link ScriptArtifact}. The tests print the exchange ({@link StepHarness#printInput}/{@code printResult})
 * and assert the duration parsing and the returned {@code "script"} handle.
 */
class GenerateStoryRunnerTest {

    private StepHarness harness;
    private GenerateStoryStepHandler handler;

    @BeforeEach
    void setUp() {
        harness = new StepHarness();
        handler = new GenerateStoryStepHandler(harness.artifactRepository(), harness.projectRepository());
    }

    @Test
    @DisplayName("GENERATE_STORY: writes a ScriptArtifact carrying the configured target duration")
    void generatesScriptWithConfiguredDuration() throws Exception {
        Project project = harness.newProject();
        StepContext ctx = harness.context(project)
            .config("targetDurationSeconds", "90")
            .build();

        harness.printInput("GENERATE_STORY", ctx);
        StepResult result = handler.execute(ctx);
        harness.printResult(result);

        UUID scriptId = result.outputArtifactIds().get("script");
        assertThat(scriptId).as("returns a 'script' handle").isNotNull();

        ScriptArtifact saved = (ScriptArtifact) harness.getArtifact(scriptId);
        assertThat(saved.getEstimatedDurationSeconds()).isEqualTo(90);
        assertThat(saved.getScriptText()).isNotBlank();
        assertThat(saved.getStatus()).isEqualTo(ArtifactStatus.READY);
        assertThat(saved.getProject()).isSameAs(project);
    }

    @Test
    @DisplayName("GENERATE_STORY: a missing/garbage targetDurationSeconds falls back to 60")
    void defaultsDurationWhenConfigUnparseable() throws Exception {
        Project project = harness.newProject();
        StepContext ctx = harness.context(project)
            .config("targetDurationSeconds", "not-a-number")
            .build();

        StepResult result = handler.execute(ctx);

        ScriptArtifact saved = (ScriptArtifact) harness.getArtifact(result.outputArtifactIds().get("script"));
        assertThat(saved.getEstimatedDurationSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("GENERATE_STORY: an unknown project id fails fast as StepExecutionException")
    void failsWhenProjectMissing() {
        StepContext ctx = harness.context(UUID.randomUUID()).build(); // never seeded

        harness.printInput("GENERATE_STORY", ctx);
        assertThatThrownBy(() -> handler.execute(ctx))
            .isInstanceOf(StepExecutionException.class)
            .hasMessageContaining("Project not found");
    }
}
