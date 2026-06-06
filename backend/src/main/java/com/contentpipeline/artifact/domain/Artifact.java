package com.contentpipeline.artifact.domain;

import com.contentpipeline.common.model.BaseEntity;
import com.contentpipeline.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "artifacts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "artifact_type", discriminatorType = DiscriminatorType.STRING, length = 50)
public abstract class Artifact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "pipeline_step_run_id", nullable = false)
    private java.util.UUID pipelineStepRunId;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ArtifactStatus status = ArtifactStatus.PENDING;

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public java.util.UUID getPipelineStepRunId() { return pipelineStepRunId; }
    public void setPipelineStepRunId(java.util.UUID pipelineStepRunId) { this.pipelineStepRunId = pipelineStepRunId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public ArtifactStatus getStatus() { return status; }
    public void setStatus(ArtifactStatus status) { this.status = status; }
}
