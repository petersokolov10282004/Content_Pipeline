package com.contentpipeline.render.domain;

public enum RenderJobStatus {
    QUEUED,
    CLAIMED,
    PROCESSING,
    COMPLETED,
    FAILED
}
