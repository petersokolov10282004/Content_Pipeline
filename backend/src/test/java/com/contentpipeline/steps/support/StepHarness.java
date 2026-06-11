package com.contentpipeline.steps.support;

import com.contentpipeline.artifact.domain.Artifact;
import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.PublishConfigArtifact;
import com.contentpipeline.artifact.domain.PublishResultArtifact;
import com.contentpipeline.artifact.domain.RenderedVideoArtifact;
import com.contentpipeline.artifact.domain.ScriptArtifact;
import com.contentpipeline.artifact.domain.SubtitleArtifact;
import com.contentpipeline.artifact.repository.ArtifactRepository;
import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.domain.AssetStatus;
import com.contentpipeline.asset.domain.AssetType;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.config.StorageProperties;
import com.contentpipeline.common.storage.StorageService;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.repository.ProjectRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A self-contained sandbox for running a single {@link com.contentpipeline.pipeline.handler.PipelineStepHandler}
 * by itself and watching its input → output, with <em>zero</em> infrastructure: no database, no
 * Temporal, no Cloudflare R2.
 *
 * <p>A handler only ever touches four collaborators — {@code ArtifactRepository},
 * {@code ProjectRepository}, {@code AssetRepository} and {@code StorageService} (plus a
 * {@code StepProgress} reporter). This harness fakes all of them with in-memory maps so a test can:
 * <ol>
 *   <li>seed the inputs a step expects ({@link #seedScript}, {@link #seedSubtitle},
 *       {@link #seedGameplayAsset}, …),</li>
 *   <li>assemble a {@link StepContext} with {@link #context},</li>
 *   <li>run the real handler, and</li>
 *   <li>inspect the artifacts it wrote via {@link #getArtifact}/{@link #stored} or simply print the
 *       whole exchange with {@link #printInput}/{@link #printResult}.</li>
 * </ol>
 *
 * <p>Mocks are created with plain {@code Mockito.mock(...)} (not the strict {@code MockitoExtension})
 * so a handler that ignores, say, the asset repository does not trip an unnecessary-stubbing error.
 */
public class StepHarness {

    /** The single bucket all seeded assets / rendered outputs live in. */
    public static final String BUCKET = "test-bucket";

    private final Map<UUID, Project> projectStore = new HashMap<>();
    private final Map<UUID, Asset> assetStore = new HashMap<>();
    private final Map<UUID, Artifact> artifactStore = new HashMap<>();
    /** Object store keyed by {@code "<bucket>/<key>"}. */
    private final Map<String, byte[]> blobStore = new HashMap<>();
    /** Phases reported by the handler under test, in order. */
    private final List<String> phases = new ArrayList<>();

    private final ArtifactRepository artifactRepository = mock(ArtifactRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final StorageProperties storageProperties =
        new StorageProperties(null, null, null, BUCKET, null, "auto");
    private final StorageService storageService = new InMemoryStorage();

    public StepHarness() {
        when(artifactRepository.save(any())).thenAnswer(inv -> {
            Artifact a = inv.getArgument(0);
            stampId(a);
            artifactStore.put(a.getId(), a);
            return a;
        });
        when(artifactRepository.findById(any()))
            .thenAnswer(inv -> Optional.ofNullable(artifactStore.get(inv.getArgument(0))));

        when(projectRepository.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            stampId(p);
            projectStore.put(p.getId(), p);
            return p;
        });
        when(projectRepository.findById(any()))
            .thenAnswer(inv -> Optional.ofNullable(projectStore.get(inv.getArgument(0))));

        when(assetRepository.findById(any()))
            .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.getArgument(0))));
    }

    // ─── Collaborators to inject into a handler ─────────────────────────────────

    public ArtifactRepository artifactRepository() { return artifactRepository; }
    public ProjectRepository projectRepository() { return projectRepository; }
    public AssetRepository assetRepository() { return assetRepository; }
    public StorageService storageService() { return storageService; }
    public StorageProperties storageProperties() { return storageProperties; }
    public List<String> phases() { return phases; }

    // ─── Seeding inputs ─────────────────────────────────────────────────────────

    public Project newProject() {
        Project p = new Project();
        p.setName("Demo project");
        p.setOwnerIdentifier("dev-user-001");
        stampId(p);
        projectStore.put(p.getId(), p);
        return p;
    }

    public ScriptArtifact seedScript(Project project, String scriptText, Integer durationSeconds) {
        ScriptArtifact a = new ScriptArtifact();
        a.setProject(project);
        a.setPipelineStepRunId(UUID.randomUUID());
        a.setScriptText(scriptText);
        a.setTitle("Seed script");
        a.setEstimatedDurationSeconds(durationSeconds);
        a.setStatus(ArtifactStatus.READY);
        return store(a);
    }

    public SubtitleArtifact seedSubtitle(Project project, String srtContent) {
        SubtitleArtifact a = new SubtitleArtifact();
        a.setProject(project);
        a.setPipelineStepRunId(UUID.randomUUID());
        a.setSrtContent(srtContent);
        a.setLineCount((int) srtContent.lines().filter(l -> !l.isBlank()).count());
        a.setStatus(ArtifactStatus.READY);
        return store(a);
    }

    public RenderedVideoArtifact seedRenderedVideo(Project project) {
        RenderedVideoArtifact a = new RenderedVideoArtifact();
        a.setProject(project);
        a.setPipelineStepRunId(UUID.randomUUID());
        a.setStorageKey("renders/seed/output.mp4");
        a.setStorageBucket(BUCKET);
        a.setFileSizeBytes(1024L);
        a.setResolution("1080x1920");
        a.setStatus(ArtifactStatus.READY);
        return store(a);
    }

    /** Seeds a GAMEPLAY_VIDEO asset and puts its bytes in the fake object store. */
    public Asset seedGameplayAsset(Project project, byte[] content) {
        Asset asset = new Asset();
        asset.setProject(project);
        asset.setName("gameplay.mp4");
        asset.setAssetType(AssetType.GAMEPLAY_VIDEO);
        asset.setStorageBucket(BUCKET);
        String key = "assets/" + project.getId() + "/gameplay.mp4";
        asset.setStorageKey(key);
        asset.setOriginalFilename("gameplay.mp4");
        asset.setContentType("video/mp4");
        asset.setSizeBytes((long) content.length);
        asset.setStatus(AssetStatus.READY);
        stampId(asset);
        assetStore.put(asset.getId(), asset);
        blobStore.put(BUCKET + "/" + key, content);
        return asset;
    }

    // ─── Building the StepContext ───────────────────────────────────────────────

    public ContextBuilder context(Project project) { return new ContextBuilder(project.getId()); }

    /** For exercising the "project not found" path: pass an id that was never seeded. */
    public ContextBuilder context(UUID projectId) { return new ContextBuilder(projectId); }

    public class ContextBuilder {
        private final UUID projectId;
        private final UUID runId = UUID.randomUUID();
        private final UUID stepRunId = UUID.randomUUID();
        private final Map<String, UUID> inArtifacts = new LinkedHashMap<>();
        private final Map<String, UUID> inAssets = new LinkedHashMap<>();
        private final Map<String, String> config = new LinkedHashMap<>();

        private ContextBuilder(UUID projectId) { this.projectId = projectId; }

        public ContextBuilder artifact(String name, UUID id) { inArtifacts.put(name, id); return this; }
        public ContextBuilder asset(String name, UUID id) { inAssets.put(name, id); return this; }
        public ContextBuilder config(String key, String value) { config.put(key, value); return this; }

        public StepContext build() {
            return new StepContext(
                runId, stepRunId, projectId,
                Map.copyOf(inArtifacts), Map.copyOf(inAssets), Map.copyOf(config),
                "dev-user-001",
                phases::add);
        }
    }

    // ─── Inspecting outputs ─────────────────────────────────────────────────────

    public Artifact getArtifact(UUID id) { return artifactStore.get(id); }

    /** All stored artifacts of a given subtype (e.g. the PublishConfig a handler wrote but did not return). */
    @SuppressWarnings("unchecked")
    public <T extends Artifact> List<T> stored(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Artifact a : artifactStore.values()) {
            if (type.isInstance(a)) out.add((T) a);
        }
        return out;
    }

    public boolean objectExists(String bucket, String key) {
        return blobStore.containsKey(bucket + "/" + key);
    }

    // ─── Pretty-printing the exchange ───────────────────────────────────────────

    public void printInput(String handlerKey, StepContext ctx) {
        System.out.println();
        System.out.println("════════════════ " + handlerKey + " ════════════════");
        System.out.println("── INPUT ───────────────────────────────────────────");
        System.out.println("  projectId      = " + ctx.projectId());
        if (!ctx.inputArtifactIds().isEmpty()) {
            System.out.println("  inputArtifacts:");
            ctx.inputArtifactIds().forEach((k, v) -> System.out.println("    " + k + " -> " + describeArtifact(v)));
        }
        if (!ctx.inputAssetIds().isEmpty()) {
            System.out.println("  inputAssets:");
            ctx.inputAssetIds().forEach((k, v) -> System.out.println("    " + k + " -> " + describeAsset(v)));
        }
        System.out.println("  stepConfig     = " + ctx.stepConfig());
    }

    public void printResult(StepResult result) {
        System.out.println("── OUTPUT ──────────────────────────────────────────");
        System.out.println("  success        = " + result.success());
        if (result.outputArtifactIds().isEmpty()) {
            System.out.println("  outputArtifacts: (none)");
        } else {
            System.out.println("  outputArtifacts:");
            result.outputArtifactIds().forEach((k, v) -> System.out.println("    " + k + " -> " + describeArtifact(v)));
        }
        if (!phases.isEmpty()) System.out.println("  phases         = " + phases);
        System.out.println();
    }

    public void printFailure(Throwable t) {
        System.out.println("── OUTPUT (threw) ──────────────────────────────────");
        System.out.println("  " + t.getClass().getSimpleName() + ": " + t.getMessage());
        if (!phases.isEmpty()) System.out.println("  phases         = " + phases);
        System.out.println();
    }

    public String describeArtifact(UUID id) {
        Artifact a = artifactStore.get(id);
        if (a == null) return id + " (not stored)";
        if (a instanceof ScriptArtifact s) {
            return "ScriptArtifact{durationSec=" + s.getEstimatedDurationSeconds()
                + ", text=\"" + snippet(s.getScriptText()) + "\"}";
        }
        if (a instanceof SubtitleArtifact s) {
            return "SubtitleArtifact{lineCount=" + s.getLineCount()
                + ", srt=\"" + snippet(s.getSrtContent()) + "\"}";
        }
        if (a instanceof RenderedVideoArtifact r) {
            return "RenderedVideoArtifact{key=" + r.getStorageKey()
                + ", sizeBytes=" + r.getFileSizeBytes() + ", res=" + r.getResolution() + "}";
        }
        if (a instanceof PublishConfigArtifact p) {
            return "PublishConfigArtifact{title=" + p.getTitle() + ", privacy=" + p.getPrivacyStatus() + "}";
        }
        if (a instanceof PublishResultArtifact p) {
            return "PublishResultArtifact{status=" + p.getPublishStatus()
                + ", videoId=" + p.getPlatformVideoId() + ", url=" + p.getPlatformUrl() + "}";
        }
        return a.getClass().getSimpleName() + "{id=" + id + "}";
    }

    public String describeAsset(UUID id) {
        Asset a = assetStore.get(id);
        if (a == null) return id + " (not stored)";
        return "Asset{type=" + a.getAssetType() + ", key=" + a.getStorageKey()
            + ", sizeBytes=" + a.getSizeBytes() + "}";
    }

    /**
     * Writes a tiny POSIX shell script that ignores its arguments and creates the file named by its
     * last argument — a stand-in for the real ffmpeg binary so {@code RENDER_VIDEO} can run end-to-end
     * without ffmpeg installed. The handler always passes the output path as the final argument.
     */
    public String fakeFfmpegWritingOutput() {
        try {
            Path script = Files.createTempFile("fake-ffmpeg", ".sh");
            Files.writeString(script, """
                #!/bin/sh
                for last; do :; done
                printf 'fake-mp4-bytes' > "$last"
                exit 0
                """);
            script.toFile().setExecutable(true);
            script.toFile().deleteOnExit();
            return script.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─── internals ──────────────────────────────────────────────────────────────

    private <T extends Artifact> T store(T artifact) {
        stampId(artifact);
        artifactStore.put(artifact.getId(), artifact);
        return artifact;
    }

    private static String snippet(String text) {
        if (text == null) return "";
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }

    /** Mimics JPA stamping an id on first persist, only if one is not already set. */
    private static void stampId(Object entity) {
        if (ReflectionTestUtils.getField(entity, "id") == null) {
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        }
    }

    /** A minimal in-memory {@link StorageService} backed by {@link #blobStore}. */
    private class InMemoryStorage implements StorageService {
        @Override
        public String generatePresignedDownloadUrl(String bucket, String key, int expiryMinutes) {
            return "https://example.test/get/" + bucket + "/" + key;
        }

        @Override
        public String generatePresignedUploadUrl(String bucket, String key, String contentType, int expiryMinutes) {
            return "https://example.test/put/" + bucket + "/" + key;
        }

        @Override
        public void put(String bucket, String key, InputStream data, long contentLength, String contentType) {
            try {
                blobStore.put(bucket + "/" + key, data.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream get(String bucket, String key) {
            byte[] bytes = blobStore.get(bucket + "/" + key);
            if (bytes == null) throw new IllegalStateException("No object at " + bucket + "/" + key);
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(String bucket, String key) {
            blobStore.remove(bucket + "/" + key);
        }

        @Override
        public boolean exists(String bucket, String key) {
            return blobStore.containsKey(bucket + "/" + key);
        }
    }
}
