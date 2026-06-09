package com.contentpipeline.pipeline.handler;

/**
 * Progress reporter handed to a step handler so it can mark which phase it is in.
 * The activity's implementation updates the PipelineStepRun's phase + heartbeat and
 * emits an SSE STEP_PROGRESS event, making intra-step execution observable (and a
 * stalled phase diagnosable). Handlers should call {@link #report} at each meaningful
 * transition (e.g. "DOWNLOADING_GAMEPLAY", "RUNNING_FFMPEG", "UPLOADING_RENDER").
 */
@FunctionalInterface
public interface StepProgress {

    /** Record the current execution phase. Cheap, side-effecting, best-effort. */
    void report(String phase);

    /** A no-op reporter for tests or contexts without progress tracking. */
    StepProgress NOOP = phase -> { };
}
