package com.contentpipeline.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface StoryPipelineWorkflow {

    @WorkflowMethod
    void run(WorkflowInput input);
}
