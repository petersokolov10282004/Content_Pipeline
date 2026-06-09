package com.contentpipeline.pipeline.run.api.dto;

import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import com.contentpipeline.project.domain.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the {@link PipelineRunResponse#from} entity→DTO mapping. This mapper is the one
 * CLAUDE.md flags as a lazy-loading hazard: with {@code open-in-view: false}, dereferencing the
 * run's {@code project}, {@code pipelineTemplate}, or {@code stepRuns} outside a transaction
 * would 500. The service layer guarantees those are initialized; here we feed a fully-populated
 * graph and assert every field is carried across and that step ordering is preserved — so a
 * dropped or mis-mapped field is caught without needing a database.
 */
class PipelineRunResponseTest {

    @Test
    @DisplayName("from: copies every run field, resolves template name, and maps step-runs in order")
    void mapsFullRunGraph() {
        UUID projectId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant started = Instant.parse("2026-01-01T00:00:00Z");

        Project project = new Project();
        setId(project, projectId);

        PipelineTemplate template = new PipelineTemplate();
        setId(template, templateId);
        template.setName("Story Pipeline");

        PipelineRun run = new PipelineRun();
        setId(run, runId);
        run.setProject(project);
        run.setPipelineTemplate(template);
        run.setStatus(PipelineRunStatus.RUNNING);
        run.setTemporalWorkflowId("story-pipeline-" + runId);
        run.setStartedAt(started);

        // Two step-runs added out of order to confirm the response reflects list order as given.
        run.getStepRuns().add(stepRun(template, 1, "GENERATE_STORY", "Generate story", StepRunStatus.COMPLETED));
        run.getStepRuns().add(stepRun(template, 2, "RENDER_VIDEO", "Render video", StepRunStatus.RUNNING));

        PipelineRunResponse response = PipelineRunResponse.from(run);

        assertThat(response.id()).isEqualTo(runId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.pipelineTemplateId()).isEqualTo(templateId);
        assertThat(response.templateName()).isEqualTo("Story Pipeline");
        assertThat(response.status()).isEqualTo(PipelineRunStatus.RUNNING);
        assertThat(response.temporalWorkflowId()).isEqualTo("story-pipeline-" + runId);
        assertThat(response.startedAt()).isEqualTo(started);

        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps()).extracting(StepRunResponse::stepOrder).containsExactly(1, 2);
        assertThat(response.steps()).extracting(StepRunResponse::stepHandlerKey)
            .containsExactly("GENERATE_STORY", "RENDER_VIDEO");
        assertThat(response.steps().get(0).status()).isEqualTo(StepRunStatus.COMPLETED);
        assertThat(response.steps().get(1).status()).isEqualTo(StepRunStatus.RUNNING);
    }

    @Test
    @DisplayName("from: a run with no step-runs maps to an empty steps list, not null")
    void mapsRunWithNoSteps() {
        Project project = new Project();
        setId(project, UUID.randomUUID());
        PipelineTemplate template = new PipelineTemplate();
        setId(template, UUID.randomUUID());
        template.setName("Empty");

        PipelineRun run = new PipelineRun();
        setId(run, UUID.randomUUID());
        run.setProject(project);
        run.setPipelineTemplate(template);
        run.setStatus(PipelineRunStatus.PENDING);
        run.setTemporalWorkflowId("wf");

        PipelineRunResponse response = PipelineRunResponse.from(run);

        assertThat(response.steps()).isEmpty();
        assertThat(response.completedAt()).isNull();
    }

    private PipelineStepRun stepRun(PipelineTemplate template, int order, String key,
                                    String name, StepRunStatus status) {
        PipelineStepDefinition def = new PipelineStepDefinition();
        setId(def, UUID.randomUUID());
        def.setPipelineTemplate(template);
        def.setStepOrder(order);
        def.setStepHandlerKey(key);
        def.setStepName(name);

        PipelineStepRun sr = new PipelineStepRun();
        setId(sr, UUID.randomUUID());
        sr.setStepDefinition(def);
        sr.setStepOrder(order);
        sr.setStatus(status);
        return sr;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
