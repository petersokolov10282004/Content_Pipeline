package com.contentpipeline.pipeline.run.api;

import com.contentpipeline.common.model.DevUser;
import com.contentpipeline.common.sse.SseEmitterRegistry;
import com.contentpipeline.pipeline.run.api.dto.CreatePipelineRunRequest;
import com.contentpipeline.pipeline.run.api.dto.PipelineRunResponse;
import com.contentpipeline.pipeline.run.service.PipelineRunService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/pipeline-runs")
public class PipelineRunController {

    private final PipelineRunService runService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public PipelineRunController(PipelineRunService runService, SseEmitterRegistry sseEmitterRegistry) {
        this.runService = runService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineRunResponse create(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @Valid @RequestBody CreatePipelineRunRequest request
    ) {
        return runService.create(userId, projectId, request);
    }

    @GetMapping
    public Page<PipelineRunResponse> list(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return runService.list(userId, projectId, PageRequest.of(page, size));
    }

    @GetMapping("/{runId}")
    public PipelineRunResponse get(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID runId
    ) {
        return runService.get(userId, projectId, runId);
    }

    /** SSE stream for live step-transition events. Frontend reconnects automatically on disconnect. */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID runId
    ) {
        runService.requireOwnedRun(userId, projectId, runId); // 404 if not found/owned
        return sseEmitterRegistry.register(runId);
    }
}
