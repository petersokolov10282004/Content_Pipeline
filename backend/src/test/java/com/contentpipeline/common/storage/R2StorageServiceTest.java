package com.contentpipeline.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the R2/S3 wrapper against a mocked {@link S3Client} and {@link S3Presigner}.
 * The AWS SDK itself is not exercised — we only verify this class's own decisions:
 *
 *  - {@code exists()} treats <em>only</em> a {@link NoSuchKeyException} as "absent" and must
 *    let any other failure (auth, network, throttling) propagate, so a transient error is
 *    never silently reported as a missing object (which would let confirm-upload pass falsely).
 *  - the presign helpers honour the requested expiry duration and return the signed URL.
 *  - delete forwards the exact bucket/key to the SDK.
 *
 * The presign call chains return real {@link URL}s via stubbed presigned-request objects.
 */
@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private R2StorageService storage;

    private final String bucket = "content-bucket";
    private final String key = "assets/p/a/clip.mp4";

    @BeforeEach
    void setUp() {
        storage = new R2StorageService(s3Client, s3Presigner);
    }

    @Test
    @DisplayName("exists: true when headObject succeeds")
    void existsTrueWhenHeadSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder().build());

        assertThat(storage.exists(bucket, key)).isTrue();
    }

    @Test
    @DisplayName("exists: false only for NoSuchKeyException (object genuinely absent)")
    void existsFalseOnNoSuchKey() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("nope").build());

        assertThat(storage.exists(bucket, key)).isFalse();
    }

    @Test
    @DisplayName("exists: a non-NoSuchKey error (e.g. auth/throttle) propagates rather than being read as 'absent'")
    void existsPropagatesOtherErrors() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(AwsServiceException.builder().message("AccessDenied").build());

        assertThatThrownBy(() -> storage.exists(bucket, key))
            .isInstanceOf(AwsServiceException.class);
    }

    @Test
    @DisplayName("generatePresignedDownloadUrl: signs a GET for the given expiry and returns the URL string")
    void presignDownloadReturnsUrl() throws Exception {
        URL url = URI.create("https://r2.example/get-signed").toURL();
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(url);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String result = storage.generatePresignedDownloadUrl(bucket, key, 15);

        assertThat(result).isEqualTo("https://r2.example/get-signed");
        ArgumentCaptor<GetObjectPresignRequest> captor =
            ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("generatePresignedUploadUrl: signs a PUT for the given expiry and returns the URL string")
    void presignUploadReturnsUrl() throws Exception {
        URL url = URI.create("https://r2.example/put-signed").toURL();
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(url);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        String result = storage.generatePresignedUploadUrl(bucket, key, "video/mp4", 30);

        assertThat(result).isEqualTo("https://r2.example/put-signed");
        ArgumentCaptor<PutObjectPresignRequest> captor =
            ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("delete: forwards the exact bucket and key to the SDK")
    void deleteForwardsBucketAndKey() {
        storage.delete(bucket, key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(bucket);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }
}
