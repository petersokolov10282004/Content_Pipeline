package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.time.Instant;

@Entity
@DiscriminatorValue("PUBLISH_RESULT")
public class PublishResultArtifact extends Artifact {

    @Column(length = 50)
    private String publishStatus;

    private String platformVideoId;

    @Column(length = 1024)
    private String platformUrl;

    private Instant publishedAt;

    @Column(name = "publish_error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getPlatformVideoId() { return platformVideoId; }
    public void setPlatformVideoId(String platformVideoId) { this.platformVideoId = platformVideoId; }
    public String getPlatformUrl() { return platformUrl; }
    public void setPlatformUrl(String platformUrl) { this.platformUrl = platformUrl; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
