package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

//describes the artifact for the script
@Entity
@DiscriminatorValue("SCRIPT")
public class ScriptArtifact extends Artifact {

    @Column(columnDefinition = "TEXT")
    private String scriptText;

    private String title;

    @Column(length = 100)
    private String genre;

    private Integer estimatedDurationSeconds;

    public String getScriptText() { return scriptText; }
    public void setScriptText(String scriptText) { this.scriptText = scriptText; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public Integer getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public void setEstimatedDurationSeconds(Integer estimatedDurationSeconds) { this.estimatedDurationSeconds = estimatedDurationSeconds; }
}
