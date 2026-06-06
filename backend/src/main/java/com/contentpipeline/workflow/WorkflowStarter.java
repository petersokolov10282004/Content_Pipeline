package com.contentpipeline.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.stereotype.Component;

/**
 * Submits new pipeline workflow executions to Temporal.
 * Each run gets a deterministic workflow ID derived from the run UUID so
 * duplicate starts are safely rejected by Temporal.
 */
@Component
public class WorkflowStarter {

    public static final String TASK_QUEUE = "story-pipeline";

    private final WorkflowClient workflowClient;

    public WorkflowStarter(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Starts the StoryPipelineWorkflow asynchronously and returns the Temporal run ID.
     * The workflow ID is {@code story-pipeline-<pipelineRunId>}.
     */
    public String start(WorkflowInput input) {
        String workflowId = "story-pipeline-" + input.pipelineRunId();

        StoryPipelineWorkflow workflow = workflowClient.newWorkflowStub(
            StoryPipelineWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TASK_QUEUE)
                .build()
        );

        WorkflowClient.start(workflow::run, input);
        return workflowId;
    }
}
