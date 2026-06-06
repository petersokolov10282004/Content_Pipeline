package com.contentpipeline.project.repository;

import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOwnerIdentifierOrderByCreatedAtDesc(String ownerIdentifier);

    List<Project> findByOwnerIdentifierAndStatusOrderByCreatedAtDesc(String ownerIdentifier, ProjectStatus status);
}
