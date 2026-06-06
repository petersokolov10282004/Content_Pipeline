package com.contentpipeline.steps.registry;

import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Auto-discovers all PipelineStepHandler beans and indexes them by handlerKey().
 * Adding a new step handler requires only annotating it with @Component — no changes here.
 */
@Component
public class StepHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(StepHandlerRegistry.class);

    private final Map<String, PipelineStepHandler> handlers;

    public StepHandlerRegistry(List<PipelineStepHandler> allHandlers) {
        this.handlers = allHandlers.stream()
            .collect(Collectors.toUnmodifiableMap(PipelineStepHandler::handlerKey, h -> h));
    }

    @PostConstruct
    void logRegistered() {
        log.info("Registered {} step handler(s): {}", handlers.size(), handlers.keySet());
    }

    public PipelineStepHandler getRequired(String key) {
        var handler = handlers.get(key);
        if (handler == null) {
            throw new PipelineException("No step handler registered for key: " + key);
        }
        return handler;
    }

    public boolean hasHandler(String key) {
        return handlers.containsKey(key);
    }
}
