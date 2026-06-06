package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PUBLISH_CONFIG")
public class PublishConfigArtifact extends Artifact {

    @Column(name = "pub_title", length = 512)
    private String title;

    @Column(name = "pub_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "pub_tags", columnDefinition = "TEXT")
    private String tags;

    @Column(length = 50)
    private String privacyStatus;

    private String playlistId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getPrivacyStatus() { return privacyStatus; }
    public void setPrivacyStatus(String privacyStatus) { this.privacyStatus = privacyStatus; }
    public String getPlaylistId() { return playlistId; }
    public void setPlaylistId(String playlistId) { this.playlistId = playlistId; }
}
