package com.contentpipeline.upload.domain;

import com.contentpipeline.common.model.BaseEntity;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.social.domain.SocialAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_jobs")
public class UploadJob extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_step_run_id", unique = true, nullable = false)
    private PipelineStepRun pipelineStepRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UploadJobStatus status = UploadJobStatus.QUEUED;

    @Column(nullable = false)
    private UUID renderedVideoArtifactId;

    @Column(nullable = false)
    private UUID publishConfigArtifactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_account_id")
    private SocialAccount socialAccount;

    private Instant startedAt;
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String uploadResponseJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Integer attemptNumber = 1;

    public PipelineStepRun getPipelineStepRun() { return pipelineStepRun; }
    public void setPipelineStepRun(PipelineStepRun pipelineStepRun) { this.pipelineStepRun = pipelineStepRun; }
    public UploadJobStatus getStatus() { return status; }
    public void setStatus(UploadJobStatus status) { this.status = status; }
    public UUID getRenderedVideoArtifactId() { return renderedVideoArtifactId; }
    public void setRenderedVideoArtifactId(UUID renderedVideoArtifactId) { this.renderedVideoArtifactId = renderedVideoArtifactId; }
    public UUID getPublishConfigArtifactId() { return publishConfigArtifactId; }
    public void setPublishConfigArtifactId(UUID publishConfigArtifactId) { this.publishConfigArtifactId = publishConfigArtifactId; }
    public SocialAccount getSocialAccount() { return socialAccount; }
    public void setSocialAccount(SocialAccount socialAccount) { this.socialAccount = socialAccount; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getUploadResponseJson() { return uploadResponseJson; }
    public void setUploadResponseJson(String uploadResponseJson) { this.uploadResponseJson = uploadResponseJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
}
