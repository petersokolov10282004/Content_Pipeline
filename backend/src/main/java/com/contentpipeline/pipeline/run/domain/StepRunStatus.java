package com.contentpipeline.pipeline.run.domain;

public enum StepRunStatus {
    PENDING,
    RUNNING,
    AWAITING_INPUT,
    COMPLETED,
    FAILED,
    SKIPPED
}
