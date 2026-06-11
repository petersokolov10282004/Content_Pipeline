package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.RenderedVideoArtifact;
import com.contentpipeline.artifact.domain.SubtitleArtifact;
import com.contentpipeline.artifact.repository.ArtifactRepository;
import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.common.storage.StorageService;
import com.contentpipeline.config.StorageProperties;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Component
public class RenderVideoStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(RenderVideoStepHandler.class);

    private final AssetRepository assetRepository;
    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;
    private final ProjectRepository projectRepository;
    private final StorageProperties storageProperties;

    @Value("${render.ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${render.temp-dir:/tmp/contentpipeline/renders}")
    private String tempDir;

    public RenderVideoStepHandler(
        AssetRepository assetRepository,
        ArtifactRepository artifactRepository,
        StorageService storageService,
        ProjectRepository projectRepository,
        StorageProperties storageProperties
    ) {
        this.assetRepository = assetRepository;
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.projectRepository = projectRepository;
        this.storageProperties = storageProperties;
    }

    @Override
    public String handlerKey() {
        return "RENDER_VIDEO";
    }

    @Override
    public StepResult execute(StepContext context) throws StepExecutionException {
        UUID subtitleArtifactId = context.inputArtifactIds().get("subtitles");
        UUID gameplayAssetId = context.inputAssetIds().get("gameplay");

        if (subtitleArtifactId == null) {
            throw new StepExecutionException("Missing required input artifact 'subtitles'", false);
        }
        if (gameplayAssetId == null) {
            throw new StepExecutionException("Missing required input asset 'gameplay'", false);
        }

        var project = projectRepository.findById(context.projectId())
            .orElseThrow(() -> new StepExecutionException("Project not found: " + context.projectId(), false));

        Asset gameplay = assetRepository.findById(gameplayAssetId)
            .orElseThrow(() -> new StepExecutionException("Gameplay asset not found: " + gameplayAssetId, false));

        SubtitleArtifact subtitleArtifact = (SubtitleArtifact) artifactRepository.findById(subtitleArtifactId)
            .orElseThrow(() -> new StepExecutionException("Subtitle artifact not found: " + subtitleArtifactId, false));

        Path workDir = null;
        try {
            // The configured temp dir (e.g. /tmp/contentpipeline/renders) may not exist yet
            // in a fresh container; createTempDirectory requires its parent to be present.
            Files.createDirectories(Path.of(tempDir));
            workDir = Files.createTempDirectory(Path.of(tempDir), "render-" + context.pipelineStepRunId());

            context.progress().report("DOWNLOADING_GAMEPLAY");
            Path gameplayFile = workDir.resolve("gameplay.mp4");
            try (InputStream stream = storageService.get(gameplay.getStorageBucket(), gameplay.getStorageKey())) {
                Files.copy(stream, gameplayFile);
            }

            Path subtitleFile = workDir.resolve("subtitles.srt");
            Files.writeString(subtitleFile, subtitleArtifact.getSrtContent() != null ? subtitleArtifact.getSrtContent() : "");

            Path outputFile = workDir.resolve("output.mp4");

            context.progress().report("RUNNING_FFMPEG");
            runFfmpeg(gameplayFile, subtitleFile, outputFile, context);

            context.progress().report("UPLOADING_RENDER");
            String outputKey = String.format("renders/%s/%s/%s/%s.mp4",
                context.projectId(), context.pipelineRunId(), context.pipelineStepRunId(), UUID.randomUUID());
            String bucket = storageProperties.bucket();
            long fileSize = Files.size(outputFile);
            try (InputStream outStream = Files.newInputStream(outputFile)) {
                storageService.put(bucket, outputKey, outStream, fileSize, "video/mp4");
            }

            RenderedVideoArtifact artifact = new RenderedVideoArtifact();
            artifact.setProject(project);
            artifact.setPipelineStepRunId(context.pipelineStepRunId());
            artifact.setStorageKey(outputKey);
            artifact.setStorageBucket(bucket);
            artifact.setFileSizeBytes(fileSize);
            artifact.setResolution("1080x1920");
            artifact.setStatus(ArtifactStatus.READY);
            RenderedVideoArtifact saved = artifactRepository.save(artifact);

            log.info("Render complete: artifact={}, key={}, run={}", saved.getId(), outputKey, context.pipelineRunId());
            return StepResult.success(Map.of("renderedVideo", saved.getId()));

        } catch (StepExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new StepExecutionException("Render failed: " + e.getMessage(), true, e);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private void runFfmpeg(Path gameplay, Path subtitles, Path output, StepContext context) throws StepExecutionException {
        // Escape the subtitle path for the FFmpeg subtitles filter (colons and backslashes must be escaped)
        String subtitlePath = subtitles.toAbsolutePath().toString()
            .replace("\\", "\\\\")
            .replace(":", "\\:");

        // Fit the source into a 1080x1920 vertical frame (scale down preserving aspect,
        // pad the rest), then burn the subtitles onto that final canvas. Without this the
        // output keeps the source resolution while the artifact metadata claims 1080x1920.
        String videoFilter = "scale=1080:1920:force_original_aspect_ratio=decrease,"
            + "pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1,"
            + "subtitles=" + subtitlePath;

        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath,
            "-i", gameplay.toAbsolutePath().toString(),
            "-vf", videoFilter,
            "-c:v", "libx264",
            "-crf", "23",
            "-preset", "fast",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            "-y",
            output.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String ffmpegOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg exited {} for run {}: {}", exitCode, context.pipelineRunId(), ffmpegOutput);
                throw new StepExecutionException("FFmpeg failed (exit " + exitCode + ")", true);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepExecutionException("FFmpeg process error: " + e.getMessage(), true, e);
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }
}
