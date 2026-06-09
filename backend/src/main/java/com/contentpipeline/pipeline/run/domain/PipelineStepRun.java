package com.contentpipeline.pipeline.run.domain;

import com.contentpipeline.common.model.BaseEntity;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pipeline_step_runs")
public class PipelineStepRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRun pipelineRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_step_definition_id", nullable = false)
    private PipelineStepDefinition stepDefinition;

    @Column(nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StepRunStatus status = StepRunStatus.PENDING;

    private Instant startedAt;
    private Instant completedAt;

    /** Free-text execution phase within the step (e.g. RUNNING_FFMPEG). Updated via progress reports. */
    @Column(length = 100)
    private String phase;

    /** Bumped on each phase change; a stale value indicates a stuck step. */
    private Instant lastHeartbeatAt;

    @Column(nullable = false)
    private Integer attemptNumber = 1;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String errorStackTrace;

    public PipelineRun getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRun pipelineRun) { this.pipelineRun = pipelineRun; }
    public PipelineStepDefinition getStepDefinition() { return stepDefinition; }
    public void setStepDefinition(PipelineStepDefinition stepDefinition) { this.stepDefinition = stepDefinition; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public StepRunStatus getStatus() { return status; }
    public void setStatus(StepRunStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getErrorStackTrace() { return errorStackTrace; }
    public void setErrorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; }
}
