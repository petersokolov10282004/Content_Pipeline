package com.contentpipeline.common.storage;

import java.io.InputStream;
//An interfae for the storage we use
public interface StorageService {

    /**
     * Generate a presigned URL for downloading an object. URL expires after {@code expiryMinutes}.
     */
    String generatePresignedDownloadUrl(String bucket, String key, int expiryMinutes);

    /**
     * Generate a presigned URL for a client to PUT an object directly. URL expires after {@code expiryMinutes}.
     */
    String generatePresignedUploadUrl(String bucket, String key, String contentType, int expiryMinutes);

    /**
     * Upload an object from a stream. Used internally by workers (render, upload).
     */
    void put(String bucket, String key, InputStream data, long contentLength, String contentType);

    /**
     * Download an object as a stream. Caller is responsible for closing the stream.
     */
    InputStream get(String bucket, String key);

    /**
     * Delete an object.
     */
    void delete(String bucket, String key);

    /**
     * Check whether an object exists. Used to confirm client-side uploads.
     */
    boolean exists(String bucket, String key);
}
