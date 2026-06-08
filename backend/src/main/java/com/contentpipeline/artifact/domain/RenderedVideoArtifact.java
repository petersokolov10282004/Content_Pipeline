package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;


//Desribes where the video is stored how big it is and other meta data about it
@Entity
@DiscriminatorValue("RENDERED_VIDEO")
public class RenderedVideoArtifact extends Artifact {

    @Column(length = 1024)
    private String storageKey;

    private String storageBucket;

    private Long fileSizeBytes;

    private Integer durationSeconds;

    @Column(length = 50)
    private String resolution;

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
