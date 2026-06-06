package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("SUBTITLE")
public class SubtitleArtifact extends Artifact {

    @Column(columnDefinition = "TEXT")
    private String srtContent;

    private Integer lineCount;

    private Integer totalWords;

    public String getSrtContent() { return srtContent; }
    public void setSrtContent(String srtContent) { this.srtContent = srtContent; }
    public Integer getLineCount() { return lineCount; }
    public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }
    public Integer getTotalWords() { return totalWords; }
    public void setTotalWords(Integer totalWords) { this.totalWords = totalWords; }
}
