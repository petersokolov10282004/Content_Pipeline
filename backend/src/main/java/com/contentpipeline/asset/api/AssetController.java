package com.contentpipeline.asset.api;

import com.contentpipeline.asset.api.dto.AssetResponse;
import com.contentpipeline.asset.api.dto.ConfirmUploadRequest;
import com.contentpipeline.asset.api.dto.CreateUploadUrlRequest;
import com.contentpipeline.asset.api.dto.DownloadUrlResponse;
import com.contentpipeline.asset.api.dto.UploadUrlResponse;
import com.contentpipeline.asset.service.AssetService;
import com.contentpipeline.common.model.DevUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createUploadUrl(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateUploadUrlRequest request
    ) {
        return assetService.createUploadUrl(userId, projectId, request);
    }

    @PostMapping("/{assetId}/confirm-upload")
    public AssetResponse confirmUpload(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID assetId,
        @RequestBody(required = false) ConfirmUploadRequest request
    ) {
        return assetService.confirmUpload(userId, projectId, assetId, request);
    }

    @GetMapping
    public List<AssetResponse> list(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId
    ) {
        return assetService.list(userId, projectId);
    }

    @GetMapping("/{assetId}")
    public AssetResponse get(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID assetId
    ) {
        return assetService.get(userId, projectId, assetId);
    }

    @GetMapping("/{assetId}/download-url")
    public DownloadUrlResponse downloadUrl(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID assetId
    ) {
        return assetService.downloadUrl(userId, projectId, assetId);
    }

    @DeleteMapping("/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @RequestHeader(value = DevUser.HEADER, required = false, defaultValue = DevUser.ID) String userId,
        @PathVariable UUID projectId,
        @PathVariable UUID assetId
    ) {
        assetService.delete(userId, projectId, assetId);
    }
}
