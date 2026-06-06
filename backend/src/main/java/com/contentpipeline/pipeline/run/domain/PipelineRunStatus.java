package com.contentpipeline.pipeline.run.domain;

public enum PipelineRunStatus {
    PENDING,
    RUNNING,
    AWAITING_INPUT,
    STEP_FAILED,
    COMPLETED,
    CANCELLED
}
