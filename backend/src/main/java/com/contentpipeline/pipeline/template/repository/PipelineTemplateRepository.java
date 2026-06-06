package com.contentpipeline.pipeline.template.repository;

import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineTemplateRepository extends JpaRepository<PipelineTemplate, UUID> {

    Optional<PipelineTemplate> findByNameAndActiveTrue(String name);

    boolean existsByName(String name);

    /**
     * Eager-fetch the step definitions so response mapping works outside a
     * transaction (open-in-view is disabled). Overrides the inherited finders.
     */
    @Override
    @EntityGraph(attributePaths = "steps")
    List<PipelineTemplate> findAll();

    @Override
    @EntityGraph(attributePaths = "steps")
    Optional<PipelineTemplate> findById(UUID id);
}
