package com.contentpipeline.artifact.domain;

/** Lifecycle state of a pipeline step's output artifact. Normal path: PENDING → READY. */
public enum ArtifactStatus {

    /** Row exists, content not written yet — step is still working. Don't consume. */
    PENDING,

    /** Content is fully written and safe to use downstream. */
    READY,

    /** Step bombed out — artifact never got usable content. Kept for debugging. */
    FAILED,

    /** Replaced by a newer version (e.g. script regenerated). Kept for history, don't consume. */
    SUPERSEDED
}
