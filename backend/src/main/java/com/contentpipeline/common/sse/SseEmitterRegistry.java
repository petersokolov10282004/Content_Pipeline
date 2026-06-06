package com.contentpipeline.common.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-run SSE emitter registry.
 * Temporal workflow implementations call {@link #emit} to push events to connected frontends.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes; client auto-reconnects

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(UUID runId) {
        var emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(runId));
        emitter.onTimeout(() -> emitters.remove(runId));
        emitter.onError(ex -> emitters.remove(runId));
        emitters.put(runId, emitter);
        return emitter;
    }

    public void emit(UUID runId, Object event) {
        var emitter = emitters.get(runId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                .name("pipeline-event")
                .data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.debug("SSE emitter for run {} failed to send, removing: {}", runId, e.getMessage());
            emitters.remove(runId);
        }
    }

    public void complete(UUID runId) {
        var emitter = emitters.remove(runId);
        if (emitter != null) emitter.complete();
    }

    public void completeWithError(UUID runId, Throwable ex) {
        var emitter = emitters.remove(runId);
        if (emitter != null) emitter.completeWithError(ex);
    }
}
