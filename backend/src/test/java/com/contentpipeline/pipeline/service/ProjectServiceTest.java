package com.contentpipeline.project.service;

import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.project.api.dto.CreateProjectRequest;
import com.contentpipeline.project.api.dto.ProjectResponse;
import com.contentpipeline.project.api.dto.UpdateProjectRequest;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.domain.ProjectStatus;
import com.contentpipeline.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ProjectService}'s own logic against a mocked {@link ProjectRepository}: the
 * ownership boundary (which is the only access control in the app today) and the partial-update
 * semantics that a malformed PATCH-style call could otherwise corrupt.
 *
 * The security-relevant invariant pinned here is the 404-not-403 rule in
 * {@link ProjectService#requireOwnedProject}: another owner's project must look identical to a
 * non-existent one, so the API never confirms the existence of resources the caller can't see.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;

    private ProjectService service;

    private final String owner = "dev-user-001";
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository);
    }

    @Test
    @DisplayName("create: stamps the caller as owner and defaults the project to ACTIVE")
    void createStampsOwnerAndActive() {
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(owner, new CreateProjectRequest("My project", "desc"));

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("My project");
        assertThat(saved.getDescription()).isEqualTo("desc");
        assertThat(saved.getOwnerIdentifier()).isEqualTo(owner);
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    @DisplayName("requireOwnedProject: returns the project when the caller owns it")
    void requireOwnedReturnsForOwner() {
        Project project = ownedProject();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(service.requireOwnedProject(owner, projectId)).isSameAs(project);
    }

    @Test
    @DisplayName("requireOwnedProject: a project owned by someone else is reported as 404, not 403 (existence not leaked)")
    void requireOwnedRejectsForeignOwnerAs404() {
        Project foreign = new Project();
        foreign.setOwnerIdentifier("someone-else");
        ReflectionTestUtils.setField(foreign, "id", projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.requireOwnedProject(owner, projectId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(projectId.toString());
    }

    @Test
    @DisplayName("requireOwnedProject: a missing project id is reported as 404")
    void requireOwnedRejectsMissingAs404() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwnedProject(owner, projectId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update: applies only the non-null fields, leaving the rest untouched")
    void updateAppliesOnlyNonNullFields() {
        Project project = ownedProject();
        project.setName("original");
        project.setDescription("original-desc");
        project.setStatus(ProjectStatus.ACTIVE);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Only the name is provided; description and status are null and must be preserved.
        service.update(owner, projectId, new UpdateProjectRequest("renamed", null, null));

        assertThat(project.getName()).isEqualTo("renamed");
        assertThat(project.getDescription()).isEqualTo("original-desc");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    @DisplayName("update: can change status (e.g. archive) without touching name or description")
    void updateCanChangeStatusOnly() {
        Project project = ownedProject();
        project.setName("keep");
        project.setDescription("keep-desc");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.update(owner, projectId, new UpdateProjectRequest(null, null, ProjectStatus.ARCHIVED));

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.getName()).isEqualTo("keep");
        assertThat(project.getDescription()).isEqualTo("keep-desc");
    }

    @Test
    @DisplayName("delete: removes a project the caller owns")
    void deleteRemovesOwnedProject() {
        Project project = ownedProject();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.delete(owner, projectId);

        verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("get: maps an owned project to a response (assetCount 0 when no assets attached)")
    void getMapsOwnedProject() {
        Project project = ownedProject();
        project.setName("Visible");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ProjectResponse response = service.get(owner, projectId);

        assertThat(response.id()).isEqualTo(projectId);
        assertThat(response.name()).isEqualTo("Visible");
        assertThat(response.assetCount()).isZero();
    }

    private Project ownedProject() {
        Project project = new Project();
        project.setOwnerIdentifier(owner);
        ReflectionTestUtils.setField(project, "id", projectId);
        return project;
    }
}
