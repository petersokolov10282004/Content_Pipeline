package com.contentpipeline.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("RENDER_CONFIG")
public class RenderConfigArtifact extends Artifact {

    @Column(columnDefinition = "TEXT")
    private String configJson;

    @Column(length = 50)
    private String outputFormat;

    private Integer targetWidthPx;
    private Integer targetHeightPx;
    private Integer targetFps;

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public Integer getTargetWidthPx() { return targetWidthPx; }
    public void setTargetWidthPx(Integer targetWidthPx) { this.targetWidthPx = targetWidthPx; }
    public Integer getTargetHeightPx() { return targetHeightPx; }
    public void setTargetHeightPx(Integer targetHeightPx) { this.targetHeightPx = targetHeightPx; }
    public Integer getTargetFps() { return targetFps; }
    public void setTargetFps(Integer targetFps) { this.targetFps = targetFps; }
}
