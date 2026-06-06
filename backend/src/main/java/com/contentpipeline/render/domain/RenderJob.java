package com.contentpipeline.render.domain;

import com.contentpipeline.common.model.BaseEntity;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "render_jobs")
public class RenderJob extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_step_run_id", unique = true, nullable = false)
    private PipelineStepRun pipelineStepRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RenderJobStatus status = RenderJobStatus.QUEUED;

    @Column(nullable = false)
    private UUID scriptArtifactId;

    @Column(nullable = false)
    private UUID subtitleArtifactId;

    @Column(nullable = false)
    private UUID gameplayAssetId;

    @Column(length = 1024)
    private String outputStorageKey;

    private String outputStorageBucket;

    private String claimedByWorkerHost;
    private Instant claimedAt;
    private Instant processingStartedAt;
    private Instant completedAt;

    @Column(nullable = false)
    private Integer attemptNumber = 1;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String ffmpegCommandLog;

    public PipelineStepRun getPipelineStepRun() { return pipelineStepRun; }
    public void setPipelineStepRun(PipelineStepRun pipelineStepRun) { this.pipelineStepRun = pipelineStepRun; }
    public RenderJobStatus getStatus() { return status; }
    public void setStatus(RenderJobStatus status) { this.status = status; }
    public UUID getScriptArtifactId() { return scriptArtifactId; }
    public void setScriptArtifactId(UUID scriptArtifactId) { this.scriptArtifactId = scriptArtifactId; }
    public UUID getSubtitleArtifactId() { return subtitleArtifactId; }
    public void setSubtitleArtifactId(UUID subtitleArtifactId) { this.subtitleArtifactId = subtitleArtifactId; }
    public UUID getGameplayAssetId() { return gameplayAssetId; }
    public void setGameplayAssetId(UUID gameplayAssetId) { this.gameplayAssetId = gameplayAssetId; }
    public String getOutputStorageKey() { return outputStorageKey; }
    public void setOutputStorageKey(String outputStorageKey) { this.outputStorageKey = outputStorageKey; }
    public String getOutputStorageBucket() { return outputStorageBucket; }
    public void setOutputStorageBucket(String outputStorageBucket) { this.outputStorageBucket = outputStorageBucket; }
    public String getClaimedByWorkerHost() { return claimedByWorkerHost; }
    public void setClaimedByWorkerHost(String claimedByWorkerHost) { this.claimedByWorkerHost = claimedByWorkerHost; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getFfmpegCommandLog() { return ffmpegCommandLog; }
    public void setFfmpegCommandLog(String ffmpegCommandLog) { this.ffmpegCommandLog = ffmpegCommandLog; }
}
