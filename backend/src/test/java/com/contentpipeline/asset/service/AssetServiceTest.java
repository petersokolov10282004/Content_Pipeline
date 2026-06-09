package com.contentpipeline.asset.service;

import com.contentpipeline.asset.api.dto.AssetResponse;
import com.contentpipeline.asset.api.dto.ConfirmUploadRequest;
import com.contentpipeline.asset.api.dto.DownloadUrlResponse;
import com.contentpipeline.asset.api.dto.CreateUploadUrlRequest;
import com.contentpipeline.asset.api.dto.UploadUrlResponse;
import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.domain.AssetStatus;
import com.contentpipeline.asset.domain.AssetType;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.common.storage.StorageService;
import com.contentpipeline.config.StorageProperties;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the asset upload lifecycle against mocked collaborators (repository,
 * storage, ProjectService). The point is the service's own logic — key construction,
 * filename sanitization, the confirm-only-after-upload guard, and the 404-not-403
 * ownership rule — not the persistence or R2 layers, which are stubbed.
 *
 * The {@code bucket} ("test-bucket") is injected via a {@link StorageProperties}
 * record. JPA would normally assign the entity id on flush; since the repository is
 * mocked, the {@code saveAndFlush} stub stamps an id with reflection to mimic that.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private ProjectService projectService;
    @Mock private StorageService storageService;

    private AssetService assetService;

    private final String userId = "dev-user-001";
    private final UUID projectId = UUID.randomUUID();
    private Project project;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties(
            "acct", "key", "secret", "test-bucket", "https://r2", "auto");
        assetService = new AssetService(assetRepository, projectService, storageService, props);

        project = new Project();
        project.setOwnerIdentifier(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
    }

    @Test
    @DisplayName("createUploadUrl: persists a PENDING asset, builds the canonical R2 key, and returns the presigned PUT URL")
    void createUploadUrlHappyPath() {
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        // Mimic JPA stamping an id on flush so the service can build the key from it.
        UUID assetId = UUID.randomUUID();
        when(assetRepository.saveAndFlush(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", assetId);
            return a;
        });
        when(storageService.generatePresignedUploadUrl(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn("https://r2/presigned-put");

        CreateUploadUrlRequest request = new CreateUploadUrlRequest(
            "Gameplay clip", AssetType.GAMEPLAY_VIDEO, "video/mp4", "clip.mp4", 1024L);

        UploadUrlResponse response = assetService.createUploadUrl(userId, projectId, request);

        String expectedKey = "assets/%s/%s/clip.mp4".formatted(projectId, assetId);
        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.uploadUrl()).isEqualTo("https://r2/presigned-put");
        assertThat(response.storageKey()).isEqualTo(expectedKey);
        assertThat(response.expiresAt()).isNotNull();

        // The persisted asset starts PENDING and lands in the configured bucket.
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).saveAndFlush(captor.capture());
        Asset saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AssetStatus.PENDING);
        assertThat(saved.getStorageBucket()).isEqualTo("test-bucket");
        assertThat(saved.getStorageKey()).isEqualTo(expectedKey);

        // The presign is requested for exactly that bucket/key/content-type.
        verify(storageService).generatePresignedUploadUrl("test-bucket", expectedKey, "video/mp4", 15);
    }

    @Test
    @DisplayName("createUploadUrl: a client filename with path separators and odd characters cannot escape the asset key prefix")
    void createUploadUrlSanitizesFilename() {
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        UUID assetId = UUID.randomUUID();
        when(assetRepository.saveAndFlush(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", assetId);
            return a;
        });
        when(storageService.generatePresignedUploadUrl(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn("https://r2/presigned-put");

        CreateUploadUrlRequest request = new CreateUploadUrlRequest(
            "evil", AssetType.OTHER, "application/octet-stream", "../../etc/pa ss wd.txt", 10L);

        UploadUrlResponse response = assetService.createUploadUrl(userId, projectId, request);

        // Only the last path segment survives, and disallowed chars (incl. spaces) become underscores.
        assertThat(response.storageKey())
            .isEqualTo("assets/%s/%s/pa_ss_wd.txt".formatted(projectId, assetId))
            .doesNotContain("..");
    }

    @Test
    @DisplayName("createUploadUrl: an unowned project surfaces as 404 and no asset is written")
    void createUploadUrlRejectsUnownedProject() {
        when(projectService.requireOwnedProject(userId, projectId))
            .thenThrow(new ResourceNotFoundException("Project", projectId));

        CreateUploadUrlRequest request = new CreateUploadUrlRequest(
            "x", AssetType.IMAGE, "image/png", "x.png", 1L);

        assertThatThrownBy(() -> assetService.createUploadUrl(userId, projectId, request))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(assetRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("confirmUpload: flips the asset to READY when the object is present in storage, and records the reported size")
    void confirmUploadMarksReady() {
        Asset asset = pendingAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(storageService.exists("test-bucket", asset.getStorageKey())).thenReturn(true);

        AssetResponse response = assetService.confirmUpload(
            userId, projectId, asset.getId(), new ConfirmUploadRequest(2048L));

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(asset.getSizeBytes()).isEqualTo(2048L);
        assertThat(response.status()).isEqualTo(AssetStatus.READY);
    }

    @Test
    @DisplayName("confirmUpload: a null body is allowed and leaves the previously declared size untouched")
    void confirmUploadToleratesNullBody() {
        Asset asset = pendingAsset();
        asset.setSizeBytes(500L);
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(storageService.exists("test-bucket", asset.getStorageKey())).thenReturn(true);

        assetService.confirmUpload(userId, projectId, asset.getId(), null);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(asset.getSizeBytes()).isEqualTo(500L);
    }

    @Test
    @DisplayName("confirmUpload: refuses to confirm (and stays PENDING) when nothing was actually uploaded to storage")
    void confirmUploadFailsWhenObjectMissing() {
        Asset asset = pendingAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(storageService.exists("test-bucket", asset.getStorageKey())).thenReturn(false);

        assertThatThrownBy(() -> assetService.confirmUpload(
                userId, projectId, asset.getId(), new ConfirmUploadRequest(1L)))
            .isInstanceOf(PipelineException.class)
            .hasMessageContaining("Upload not found in storage");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PENDING);
    }

    @Test
    @DisplayName("downloadUrl: presigns a GET for the owned asset's key with the 60-minute expiry and reports a matching expiresAt")
    void downloadUrlHappyPath() {
        Asset asset = new Asset();
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "id", assetId);
        asset.setProject(project);
        asset.setStorageBucket("test-bucket");
        asset.setStorageKey("assets/%s/%s/clip.mp4".formatted(projectId, assetId));

        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(storageService.generatePresignedDownloadUrl("test-bucket", asset.getStorageKey(), 60))
            .thenReturn("https://r2/presigned-get");

        java.time.Instant before = java.time.Instant.now();
        DownloadUrlResponse response = assetService.downloadUrl(userId, projectId, assetId);
        java.time.Instant after = java.time.Instant.now();

        assertThat(response.downloadUrl()).isEqualTo("https://r2/presigned-get");
        // expiresAt is "now + 60m"; bound it by the call window so a wrong duration is caught.
        assertThat(response.expiresAt())
            .isBetween(before.plus(java.time.Duration.ofMinutes(60)),
                       after.plus(java.time.Duration.ofMinutes(60)));
        verify(storageService).generatePresignedDownloadUrl("test-bucket", asset.getStorageKey(), 60);
    }

    @Test
    @DisplayName("downloadUrl: an asset under a different project surfaces as 404 and no presign is requested")
    void downloadUrlRejectsCrossProjectAccess() {
        Project otherProject = new Project();
        otherProject.setOwnerIdentifier(userId);
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());

        Asset asset = new Asset();
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "id", assetId);
        asset.setProject(otherProject);

        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.downloadUrl(userId, projectId, assetId))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(storageService, never()).generatePresignedDownloadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("requireOwnedAsset: an asset belonging to a different project is reported as 404, not leaked as existing")
    void requireOwnedAssetRejectsCrossProjectAccess() {
        // Asset exists, but under a different project than the one in the URL.
        Project otherProject = new Project();
        otherProject.setOwnerIdentifier(userId);
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());

        Asset asset = new Asset();
        asset.setProject(otherProject);
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());

        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.requireOwnedAsset(userId, projectId, asset.getId()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(asset.getId().toString());
    }

    @Test
    @DisplayName("delete: swallows a storage failure (e.g. object never uploaded) and still removes the DB row")
    void deleteIgnoresStorageFailure() {
        Asset asset = pendingAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        org.mockito.Mockito.doThrow(new RuntimeException("R2 down"))
            .when(storageService).delete(anyString(), anyString());

        assetService.delete(userId, projectId, asset.getId());

        verify(assetRepository).delete(asset);
    }

    /** A PENDING asset already wired to the owned project, with a stamped id and key. */
    private Asset pendingAsset() {
        when(projectService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(project);
        Asset asset = new Asset();
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "id", assetId);
        asset.setProject(project);
        asset.setName("clip");
        asset.setAssetType(AssetType.GAMEPLAY_VIDEO);
        asset.setStorageBucket("test-bucket");
        asset.setStorageKey("assets/%s/%s/clip.mp4".formatted(projectId, assetId));
        asset.setOriginalFilename("clip.mp4");
        asset.setStatus(AssetStatus.PENDING);
        return asset;
    }
}
