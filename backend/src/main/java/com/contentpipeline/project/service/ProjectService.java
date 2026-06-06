package com.contentpipeline.project.service;

import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.project.api.dto.CreateProjectRequest;
import com.contentpipeline.project.api.dto.ProjectResponse;
import com.contentpipeline.project.api.dto.UpdateProjectRequest;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.domain.ProjectStatus;
import com.contentpipeline.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse create(String ownerIdentifier, CreateProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerIdentifier(ownerIdentifier);
        project.setStatus(ProjectStatus.ACTIVE);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String ownerIdentifier) {
        return projectRepository.findByOwnerIdentifierOrderByCreatedAtDesc(ownerIdentifier)
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(String ownerIdentifier, UUID projectId) {
        return ProjectResponse.from(requireOwnedProject(ownerIdentifier, projectId));
    }

    public ProjectResponse update(String ownerIdentifier, UUID projectId, UpdateProjectRequest request) {
        Project project = requireOwnedProject(ownerIdentifier, projectId);
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        return ProjectResponse.from(project);
    }

    public void delete(String ownerIdentifier, UUID projectId) {
        Project project = requireOwnedProject(ownerIdentifier, projectId);
        projectRepository.delete(project);
    }

    /**
     * Loads a project and enforces that it belongs to the caller. Throws
     * {@link ResourceNotFoundException} (404, not 403) for unowned projects so the
     * existence of other owners' projects is not leaked.
     */
    @Transactional(readOnly = true)
    public Project requireOwnedProject(String ownerIdentifier, UUID projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!project.getOwnerIdentifier().equals(ownerIdentifier)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return project;
    }
}
