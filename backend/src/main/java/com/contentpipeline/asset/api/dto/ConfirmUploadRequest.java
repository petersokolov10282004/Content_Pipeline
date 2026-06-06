package com.contentpipeline.asset.api.dto;

import jakarta.validation.constraints.Positive;

/**
 * Step 3 of the upload flow. Body is optional; {@code sizeBytes} lets the client
 * report the actual uploaded size (the declared size at upload-url time is a hint).
 */
public record ConfirmUploadRequest(
    @Positive Long sizeBytes
) {}
