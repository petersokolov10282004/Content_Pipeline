package com.contentpipeline.pipeline.run.repository;

import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    Page<PipelineRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<PipelineRun> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, PipelineRunStatus status, Pageable pageable);

    Optional<PipelineRun> findByTemporalWorkflowId(String workflowId);
}
