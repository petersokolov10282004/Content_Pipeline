package com.contentpipeline.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StoryPipelineWorkflowImpl implements StoryPipelineWorkflow {

    private final PipelineActivities activities = Workflow.newActivityStub(
        PipelineActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(5))
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    @Override
    public void run(WorkflowInput input) {
        UUID runId = input.pipelineRunId();
        Map<String, UUID> artifacts = new HashMap<>();
        Map<String, UUID> assets = new HashMap<>(input.inputAssets());

        try {
            // Step 1: Generate story script → "script"
            Map<String, UUID> out1 = activities.executeStep(
                runId, input.stepRunIds().get(0), "GENERATE_STORY",
                Map.copyOf(artifacts), Map.copyOf(assets));
            artifacts.putAll(out1);

            // Step 2: Generate subtitles → "subtitles"
            Map<String, UUID> out2 = activities.executeStep(
                runId, input.stepRunIds().get(1), "GENERATE_SUBTITLES",
                Map.copyOf(artifacts), Map.copyOf(assets));
            artifacts.putAll(out2);

            // Step 3: Render video → "renderedVideo"
            Map<String, UUID> out3 = activities.executeStep(
                runId, input.stepRunIds().get(2), "RENDER_VIDEO",
                Map.copyOf(artifacts), Map.copyOf(assets));
            artifacts.putAll(out3);

            // Step 4: Upload to YouTube → "publishResult"
            Map<String, UUID> out4 = activities.executeStep(
                runId, input.stepRunIds().get(3), "UPLOAD_VIDEO",
                Map.copyOf(artifacts), Map.copyOf(assets));
            artifacts.putAll(out4);

            activities.completeRun(runId, true);

        } catch (Exception e) {
            activities.completeRun(runId, false);
            throw e;
        }
    }
}
