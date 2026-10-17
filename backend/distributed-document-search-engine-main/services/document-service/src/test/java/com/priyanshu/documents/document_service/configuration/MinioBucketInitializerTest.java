package com.priyanshu.documents.document_service.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

class MinioBucketInitializerTest {
    @Test
    void keepsExistingBucketUntouched() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        new MinioBucketInitializer(client, "documents").run(null);
        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void createsMissingBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        new MinioBucketInitializer(client, "documents").run(null);
        verify(client).makeBucket(argThat(args -> "documents".equals(args.bucket())));
    }

    @Test
    void surfacesConnectionOrPermissionFailures() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenThrow(new IllegalStateException("Storage unavailable"));
        assertThatThrownBy(() -> new MinioBucketInitializer(client, "documents").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
    }
}
