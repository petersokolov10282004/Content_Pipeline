package com.contentpipeline.render.repository;

import com.contentpipeline.render.domain.RenderJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RenderJobRepository extends JpaRepository<RenderJob, UUID> {

    Optional<RenderJob> findByPipelineStepRunId(UUID pipelineStepRunId);
}
