package com.contentpipeline.asset.service;

import com.contentpipeline.asset.api.dto.AssetResponse;
import com.contentpipeline.asset.api.dto.ConfirmUploadRequest;
import com.contentpipeline.asset.api.dto.CreateUploadUrlRequest;
import com.contentpipeline.asset.api.dto.DownloadUrlResponse;
import com.contentpipeline.asset.api.dto.UploadUrlResponse;
import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.domain.AssetStatus;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.common.storage.StorageService;
import com.contentpipeline.config.StorageProperties;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AssetService {

    private static final int UPLOAD_URL_EXPIRY_MINUTES = 15;
    private static final int DOWNLOAD_URL_EXPIRY_MINUTES = 60;

    private final AssetRepository assetRepository;
    private final ProjectService projectService;
    private final StorageService storageService;
    private final String bucket;

    public AssetService(
        AssetRepository assetRepository,
        ProjectService projectService,
        StorageService storageService,
        StorageProperties storageProperties
    ) {
        this.assetRepository = assetRepository;
        this.projectService = projectService;
        this.storageService = storageService;
        this.bucket = storageProperties.bucket();
    }

    /**
     * Step 1: create a PENDING asset row and hand back a presigned PUT URL the
     * client uploads to directly. The R2 key follows {@code assets/{projectId}/{assetId}/{filename}}.
     */
    public UploadUrlResponse createUploadUrl(String userId, UUID projectId, CreateUploadUrlRequest request) {
        Project project = projectService.requireOwnedProject(userId, projectId);

        Asset asset = new Asset();
        asset.setProject(project);
        asset.setName(request.name());
        asset.setAssetType(request.assetType());
        asset.setContentType(request.contentType());
        asset.setOriginalFilename(request.originalFilename());
        asset.setSizeBytes(request.sizeBytes());
        asset.setStorageBucket(bucket);
        asset.setStatus(AssetStatus.PENDING);
        // storageKey is NOT NULL but depends on the generated id; set a placeholder,
        // flush to obtain the id, then write the canonical key (dirty-checked at commit).
        asset.setStorageKey("pending");
        Asset saved = assetRepository.saveAndFlush(asset);

        String key = buildStorageKey(projectId, saved.getId(), request.originalFilename());
        saved.setStorageKey(key);

        String uploadUrl = storageService.generatePresignedUploadUrl(
            bucket, key, request.contentType(), UPLOAD_URL_EXPIRY_MINUTES);

        return new UploadUrlResponse(
            saved.getId(),
            uploadUrl,
            key,
            Instant.now().plus(Duration.ofMinutes(UPLOAD_URL_EXPIRY_MINUTES))
        );
    }

    /**
     * Step 3: verify the object actually landed in R2, then flip the asset to READY.
     */
    public AssetResponse confirmUpload(String userId, UUID projectId, UUID assetId, ConfirmUploadRequest request) {
        Asset asset = requireOwnedAsset(userId, projectId, assetId);

        if (!storageService.exists(bucket, asset.getStorageKey())) {
            throw new PipelineException(
                "Upload not found in storage for asset " + assetId + "; confirm only after the PUT completes");
        }

        if (request != null && request.sizeBytes() != null) {
            asset.setSizeBytes(request.sizeBytes());
        }
        asset.setStatus(AssetStatus.READY);
        return AssetResponse.from(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list(String userId, UUID projectId) {
        projectService.requireOwnedProject(userId, projectId);
        return assetRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(AssetResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(String userId, UUID projectId, UUID assetId) {
        return AssetResponse.from(requireOwnedAsset(userId, projectId, assetId));
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(String userId, UUID projectId, UUID assetId) {
        Asset asset = requireOwnedAsset(userId, projectId, assetId);
        String url = storageService.generatePresignedDownloadUrl(
            bucket, asset.getStorageKey(), DOWNLOAD_URL_EXPIRY_MINUTES);
        return new DownloadUrlResponse(
            url,
            Instant.now().plus(Duration.ofMinutes(DOWNLOAD_URL_EXPIRY_MINUTES))
        );
    }

    public void delete(String userId, UUID projectId, UUID assetId) {
        Asset asset = requireOwnedAsset(userId, projectId, assetId);
        // Best-effort R2 cleanup; the DB row is the source of truth.
        try {
            storageService.delete(asset.getStorageBucket(), asset.getStorageKey());
        } catch (RuntimeException ignored) {
            // object may never have been uploaded (PENDING asset) — ignore
        }
        assetRepository.delete(asset);
    }

    /**
     * Loads an asset and enforces both project ownership and that the asset
     * belongs to that project. 404 (not 403) to avoid leaking existence.
     */
    @Transactional(readOnly = true)
    public Asset requireOwnedAsset(String userId, UUID projectId, UUID assetId) {
        projectService.requireOwnedProject(userId, projectId);
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
        if (!asset.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        return asset;
    }

    private static String buildStorageKey(UUID projectId, UUID assetId, String originalFilename) {
        return "assets/%s/%s/%s".formatted(projectId, assetId, sanitizeFilename(originalFilename));
    }

    /**
     * Strip path separators and control characters so a client-supplied filename
     * can't escape the asset's key prefix.
     */
    private static String sanitizeFilename(String filename) {
        String base = filename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base.isBlank() ? "file" : base;
    }
}
