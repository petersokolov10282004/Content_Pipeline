package com.contentpipeline.pipeline.run.repository;

import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PipelineStepRunRepository extends JpaRepository<PipelineStepRun, UUID> {

    List<PipelineStepRun> findByPipelineRunIdOrderByStepOrderAsc(UUID pipelineRunId);
}
