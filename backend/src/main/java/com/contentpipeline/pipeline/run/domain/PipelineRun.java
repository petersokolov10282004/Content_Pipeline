package com.contentpipeline.pipeline.run.domain;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.common.model.BaseEntity;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import com.contentpipeline.project.domain.Project;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "pipeline_runs")
public class PipelineRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_template_id", nullable = false)
    private PipelineTemplate pipelineTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PipelineRunStatus status = PipelineRunStatus.PENDING;

    private Instant startedAt;
    private Instant completedAt;

    @Column(nullable = false, length = 512)
    private String temporalWorkflowId;

    @Column(length = 512)
    private String temporalRunId;

    @Column(columnDefinition = "TEXT")
    private String inputParamsJson;

    @OneToMany(mappedBy = "pipelineRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<PipelineStepRun> stepRuns = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "pipeline_run_input_assets",
        joinColumns = @JoinColumn(name = "pipeline_run_id"),
        inverseJoinColumns = @JoinColumn(name = "asset_id")
    )
    private Set<Asset> inputAssets = new HashSet<>();

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public PipelineTemplate getPipelineTemplate() { return pipelineTemplate; }
    public void setPipelineTemplate(PipelineTemplate pipelineTemplate) { this.pipelineTemplate = pipelineTemplate; }
    public PipelineRunStatus getStatus() { return status; }
    public void setStatus(PipelineRunStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getTemporalWorkflowId() { return temporalWorkflowId; }
    public void setTemporalWorkflowId(String temporalWorkflowId) { this.temporalWorkflowId = temporalWorkflowId; }
    public String getTemporalRunId() { return temporalRunId; }
    public void setTemporalRunId(String temporalRunId) { this.temporalRunId = temporalRunId; }
    public String getInputParamsJson() { return inputParamsJson; }
    public void setInputParamsJson(String inputParamsJson) { this.inputParamsJson = inputParamsJson; }
    public List<PipelineStepRun> getStepRuns() { return stepRuns; }
    public Set<Asset> getInputAssets() { return inputAssets; }
}
