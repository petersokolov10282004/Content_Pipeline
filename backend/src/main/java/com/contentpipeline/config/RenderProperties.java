package com.contentpipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render")
public record RenderProperties(
    FfmpegProperties ffmpeg,
    String tempDir,
    WorkerProperties worker
) {
    public record FfmpegProperties(String path) {}
    public record WorkerProperties(long pollIntervalMs, int claimTimeoutMinutes) {}
}
