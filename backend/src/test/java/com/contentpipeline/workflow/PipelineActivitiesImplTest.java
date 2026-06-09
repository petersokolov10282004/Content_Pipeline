package com.contentpipeline.workflow;

import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.common.sse.SseEmitterRegistry;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.steps.registry.StepHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link PipelineActivitiesImpl#executeStep}, the Temporal Activity that drives a
 * single step. Its non-negotiable contract (see CLAUDE.md "Run lifecycle"): mark the step
 * RUNNING, invoke the handler, mark it COMPLETED — and on <em>any</em> failure mark it FAILED,
 * <em>never</em> leaving it stuck RUNNING. We also pin down the StepContext wiring and the
 * fail-fast lookups for unknown ids.
 *
 * Collaborators (both repositories, the handler registry, the handler) are mocked. Real
 * {@link PipelineRun}/{@link PipelineStepRun} entities are used so the status transitions and
 * timestamps the activity writes are directly observable. Ids are stamped via reflection to
 * mimic what JPA assigns on persist.
 *
 * Note on {@code Activity.wrap}: the production code rethrows failures through Temporal's
 * {@code Activity.wrap}, which (off a worker thread) returns a RuntimeException wrapping the
 * original cause. The tests therefore assert on the propagated cause rather than the wrapper
 * type, which is what callers ultimately care about.
 */
@ExtendWith(MockitoExtension.class)
class PipelineActivitiesImplTest {

    @Mock private PipelineRunRepository runRepository;
    @Mock private PipelineStepRunRepository stepRunRepository;
    @Mock private StepHandlerRegistry handlerRegistry;
    @Mock private PipelineStepHandler handler;
    @Mock private SseEmitterRegistry sseEmitterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PipelineActivitiesImpl activities;

    private final UUID runId = UUID.randomUUID();
    private final UUID stepRunId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final String handlerKey = "GENERATE_STORY";

    private PipelineRun run;
    private PipelineStepRun stepRun;

    @BeforeEach
    void setUp() {
        activities = new PipelineActivitiesImpl(runRepository, stepRunRepository, handlerRegistry, objectMapper, sseEmitterRegistry);

        Project project = new Project();
        setId(project, projectId);

        run = new PipelineRun();
        run.setProject(project);
        setId(run, runId);

        PipelineStepDefinition stepDef = new PipelineStepDefinition();
        stepDef.setStepHandlerKey(handlerKey);
        stepDef.setConfigJson(null);

        stepRun = new PipelineStepRun();
        stepRun.setStepOrder(1);
        stepRun.setStatus(StepRunStatus.PENDING);
        stepRun.setStepDefinition(stepDef);
        setId(stepRun, stepRunId);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("executeStep: PENDING → RUNNING → COMPLETED, stamps timestamps, returns the handler's artifacts")
    void executeStepCompletesAndReturnsArtifacts() throws Exception {
        stubLookups();
        UUID scriptId = UUID.randomUUID();
        when(handlerRegistry.getRequired(handlerKey)).thenReturn(handler);
        when(handler.execute(any(StepContext.class)))
            .thenReturn(StepResult.success(Map.of("script", scriptId)));

        Map<String, UUID> output = activities.executeStep(
            runId, stepRunId, handlerKey, Map.of(), Map.of());

        assertThat(output).containsExactly(Map.entry("script", scriptId));
        assertThat(stepRun.getStatus()).isEqualTo(StepRunStatus.COMPLETED);
        assertThat(stepRun.getStartedAt()).isNotNull();
        assertThat(stepRun.getCompletedAt()).isNotNull();
        assertThat(stepRun.getErrorMessage()).isNull();
        // The step is saved at least twice: once on RUNNING, once on COMPLETED.
        verify(stepRunRepository, atLeastOnce()).save(stepRun);
    }

    @Test
    @DisplayName("executeStep: assembles the StepContext from the run (project id) and forwards both input maps")
    void executeStepWiresStepContext() throws Exception {
        stubLookups();
        when(handlerRegistry.getRequired(handlerKey)).thenReturn(handler);
        when(handler.execute(any(StepContext.class))).thenReturn(StepResult.success(Map.of()));

        UUID subtitleArtifact = UUID.randomUUID();
        UUID gameplayAsset = UUID.randomUUID();
        Map<String, UUID> inputArtifacts = Map.of("subtitles", subtitleArtifact);
        Map<String, UUID> inputAssets = Map.of("gameplay", gameplayAsset);

        activities.executeStep(runId, stepRunId, handlerKey, inputArtifacts, inputAssets);

        ArgumentCaptor<StepContext> ctx = ArgumentCaptor.forClass(StepContext.class);
        verify(handler).execute(ctx.capture());
        StepContext c = ctx.getValue();
        assertThat(c.pipelineRunId()).isEqualTo(runId);
        assertThat(c.pipelineStepRunId()).isEqualTo(stepRunId);
        assertThat(c.projectId()).isEqualTo(projectId);
        assertThat(c.inputArtifactIds()).containsExactly(Map.entry("subtitles", subtitleArtifact));
        assertThat(c.inputAssetIds()).containsExactly(Map.entry("gameplay", gameplayAsset));
    }

    // ------------------------------------------------------------------
    // Failure path — the core invariant: never left RUNNING
    // ------------------------------------------------------------------

    @Test
    @DisplayName("executeStep: a handler StepExecutionException marks the step FAILED (never RUNNING) and rethrows the cause")
    void executeStepMarksFailedOnHandlerException() throws Exception {
        stubLookups();
        when(handlerRegistry.getRequired(handlerKey)).thenReturn(handler);
        when(handler.execute(any(StepContext.class)))
            .thenThrow(new StepExecutionException("Claude API timed out", false));

        assertThatThrownBy(() -> activities.executeStep(runId, stepRunId, handlerKey, Map.of(), Map.of()))
            .satisfies(t -> assertThat(rootCause(t))
                .isInstanceOf(StepExecutionException.class)
                .hasMessageContaining("Claude API timed out"));

        assertThat(stepRun.getStatus()).isEqualTo(StepRunStatus.FAILED);
        assertThat(stepRun.getStatus()).isNotEqualTo(StepRunStatus.RUNNING);
        assertThat(stepRun.getCompletedAt()).isNotNull();
        assertThat(stepRun.getErrorMessage()).isEqualTo("Claude API timed out");
    }

    @Test
    @DisplayName("executeStep: an unknown handler key (registry throws) also marks the step FAILED, not RUNNING")
    void executeStepMarksFailedWhenHandlerMissing() {
        stubLookups();
        when(handlerRegistry.getRequired(handlerKey))
            .thenThrow(new PipelineException("No step handler registered for key: " + handlerKey));

        assertThatThrownBy(() -> activities.executeStep(runId, stepRunId, handlerKey, Map.of(), Map.of()))
            .satisfies(t -> assertThat(rootCause(t)).isInstanceOf(PipelineException.class));

        assertThat(stepRun.getStatus()).isEqualTo(StepRunStatus.FAILED);
        assertThat(stepRun.getErrorMessage()).contains("No step handler registered");
    }

    @Test
    @DisplayName("executeStep: an unexpected RuntimeException from the handler still leaves the step FAILED with the message")
    void executeStepMarksFailedOnUnexpectedRuntime() throws Exception {
        stubLookups();
        when(handlerRegistry.getRequired(handlerKey)).thenReturn(handler);
        when(handler.execute(any(StepContext.class)))
            .thenThrow(new NullPointerException("npe in handler"));

        assertThatThrownBy(() -> activities.executeStep(runId, stepRunId, handlerKey, Map.of(), Map.of()))
            .satisfies(t -> assertThat(rootCause(t)).isInstanceOf(NullPointerException.class));

        assertThat(stepRun.getStatus()).isEqualTo(StepRunStatus.FAILED);
        assertThat(stepRun.getErrorMessage()).isEqualTo("npe in handler");
    }

    // ------------------------------------------------------------------
    // Fail-fast lookups
    // ------------------------------------------------------------------

    @Test
    @DisplayName("executeStep: an unknown stepRunId fails before any handler runs")
    void executeStepRejectsUnknownStepRun() throws Exception {
        when(stepRunRepository.findById(stepRunId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activities.executeStep(runId, stepRunId, handlerKey, Map.of(), Map.of()))
            .satisfies(t -> assertThat(rootCause(t)).hasMessageContaining(stepRunId.toString()));

        verify(handler, never()).execute(any());
        verify(handlerRegistry, never()).getRequired(any());
    }

    @Test
    @DisplayName("executeStep: an unknown pipelineRunId fails before any handler runs")
    void executeStepRejectsUnknownRun() throws Exception {
        when(stepRunRepository.findById(stepRunId)).thenReturn(Optional.of(stepRun));
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activities.executeStep(runId, stepRunId, handlerKey, Map.of(), Map.of()))
            .satisfies(t -> assertThat(rootCause(t)).hasMessageContaining(runId.toString()));

        verify(handler, never()).execute(any());
    }

    // ------------------------------------------------------------------
    // completeRun — run-status callback
    // ------------------------------------------------------------------

    @Test
    @DisplayName("completeRun: flips PipelineRun to COMPLETED and stamps completedAt on success")
    void completeRunSetsCompleted() {
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        activities.completeRun(runId, true);

        assertThat(run.getStatus()).isEqualTo(PipelineRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
        verify(runRepository).save(run);
    }

    @Test
    @DisplayName("completeRun: flips PipelineRun to STEP_FAILED and stamps completedAt on failure")
    void completeRunSetsStepFailed() {
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        activities.completeRun(runId, false);

        assertThat(run.getStatus()).isEqualTo(PipelineRunStatus.STEP_FAILED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void stubLookups() {
        when(stepRunRepository.findById(stepRunId)).thenReturn(Optional.of(stepRun));
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
    }

    /** Unwraps Temporal's Activity.wrap (and any nesting) down to the original cause. */
    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
