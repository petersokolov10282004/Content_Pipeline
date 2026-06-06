package com.contentpipeline.project.api;

import com.contentpipeline.common.model.DevUser;
import com.contentpipeline.project.api.dto.CreateProjectRequest;
import com.contentpipeline.project.api.dto.ProjectResponse;
import com.contentpipeline.project.api.dto.UpdateProjectRequest;
import com.contentpipeline.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse created = projectService.create(userId, request);
        return ResponseEntity
            .created(URI.create("/api/v1/projects/" + created.id()))
            .body(created);
    }

    @GetMapping
    public List<ProjectResponse> list(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId
    ) {
        return projectService.list(userId);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId
    ) {
        return projectService.get(userId, projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(userId, projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId
    ) {
        projectService.delete(userId, projectId);
    }
}
