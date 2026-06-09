package com.contentpipeline.workflow;

import com.contentpipeline.common.sse.SseEmitterRegistry;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.repository.PipelineStepRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persists a step's intra-execution phase + heartbeat and emits an SSE STEP_PROGRESS event.
 *
 * This is a separate bean (not a method on PipelineActivitiesImpl) for two reasons:
 *  1. {@code REQUIRES_NEW} must go through the Spring proxy — a self-call inside the activity
 *     would bypass it and silently run in the caller's transaction.
 *  2. Phase writes need to COMMIT immediately so progress is visible mid-step (e.g. while a
 *     long FFmpeg render is still running), independent of the outer step transaction.
 */
@Service
public class StepProgressService {

    private static final Logger log = LoggerFactory.getLogger(StepProgressService.class);

    private final PipelineStepRunRepository stepRunRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    public StepProgressService(
        PipelineStepRunRepository stepRunRepository,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.stepRunRepository = stepRunRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void report(UUID pipelineRunId, UUID stepRunId, String stepHandlerKey, String phase) {
        Instant now = Instant.now();
        // Best-effort: a progress write must never fail the step it is reporting on.
        try {
            PipelineStepRun stepRun = stepRunRepository.findById(stepRunId).orElse(null);
            if (stepRun != null) {
                stepRun.setPhase(phase);
                stepRun.setLastHeartbeatAt(now);
                stepRunRepository.save(stepRun);
            }
        } catch (Exception e) {
            log.warn("Failed to persist progress phase '{}' for step {}: {}", phase, stepRunId, e.getMessage());
        }

        sseEmitterRegistry.emit(pipelineRunId, Map.of(
            "type", "STEP_PROGRESS",
            "step", stepHandlerKey,
            "phase", phase,
            "timestamp", now.toString()
        ));
    }
}
