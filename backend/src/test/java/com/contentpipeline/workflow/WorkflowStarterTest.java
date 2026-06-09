package com.contentpipeline.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the only logic {@link WorkflowStarter} owns: deriving a deterministic Temporal
 * workflow id from the run id ({@code story-pipeline-<runId>}) and targeting the correct task
 * queue. CLAUDE.md relies on this determinism so a duplicate start for the same run is rejected
 * by Temporal rather than spawning a second workflow.
 *
 * Temporal is entirely mocked: the {@link WorkflowClient} instance hands back a mock stub, and
 * the static {@code WorkflowClient.start(...)} dispatch is stubbed out via {@code mockStatic} so
 * no real connection is attempted. We assert on the returned id and the captured
 * {@link WorkflowOptions}, which is the contract callers (PipelineRunService) depend on.
 */
class WorkflowStarterTest {

    @Test
    @DisplayName("start: builds workflow id 'story-pipeline-<runId>' on the story-pipeline task queue and returns it")
    void startDerivesDeterministicWorkflowId() {
        WorkflowClient client = mock(WorkflowClient.class);
        StoryPipelineWorkflow stub = mock(StoryPipelineWorkflow.class);

        ArgumentCaptor<WorkflowOptions> optionsCaptor = ArgumentCaptor.forClass(WorkflowOptions.class);
        when(client.newWorkflowStub(eq(StoryPipelineWorkflow.class), optionsCaptor.capture()))
            .thenReturn(stub);

        UUID runId = UUID.randomUUID();
        WorkflowInput input = new WorkflowInput(runId, List.of(UUID.randomUUID()), Map.of());

        String workflowId;
        // Neutralize the static fire-and-forget start so no Temporal call is made. The start(...)
        // overloads are ambiguous to match individually, so stub the whole class to do nothing by
        // default and only un-stub the instance method we actually exercise (newWorkflowStub).
        try (MockedStatic<WorkflowClient> statics =
                 mockStatic(WorkflowClient.class, invocation -> null)) {
            WorkflowStarter starter = new WorkflowStarter(client);
            workflowId = starter.start(input);
        }

        assertThat(workflowId).isEqualTo("story-pipeline-" + runId);

        WorkflowOptions options = optionsCaptor.getValue();
        assertThat(options.getWorkflowId()).isEqualTo("story-pipeline-" + runId);
        assertThat(options.getTaskQueue()).isEqualTo(WorkflowStarter.TASK_QUEUE);
        verify(client).newWorkflowStub(eq(StoryPipelineWorkflow.class), any(WorkflowOptions.class));
    }

    @Test
    @DisplayName("start: different run ids yield different workflow ids (no collision)")
    void startProducesDistinctIdsPerRun() {
        WorkflowClient client = mock(WorkflowClient.class);
        when(client.newWorkflowStub(eq(StoryPipelineWorkflow.class), any(WorkflowOptions.class)))
            .thenReturn(mock(StoryPipelineWorkflow.class));

        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();

        try (MockedStatic<WorkflowClient> statics =
                 mockStatic(WorkflowClient.class, invocation -> null)) {
            WorkflowStarter starter = new WorkflowStarter(client);

            String idA = starter.start(new WorkflowInput(runA, List.of(), Map.of()));
            String idB = starter.start(new WorkflowInput(runB, List.of(), Map.of()));

            assertThat(idA).isNotEqualTo(idB);
            assertThat(idA).isEqualTo("story-pipeline-" + runA);
            assertThat(idB).isEqualTo("story-pipeline-" + runB);
        }
    }
}
