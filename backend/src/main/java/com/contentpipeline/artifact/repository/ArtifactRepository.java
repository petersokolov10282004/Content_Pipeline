package com.contentpipeline.artifact.repository;

import com.contentpipeline.artifact.domain.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {}
