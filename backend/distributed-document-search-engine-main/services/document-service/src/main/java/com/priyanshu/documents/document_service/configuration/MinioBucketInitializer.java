package com.priyanshu.documents.document_service.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;

@Component
public class MinioBucketInitializer implements ApplicationRunner {
    private final MinioClient client;
    private final String bucket;

    public MinioBucketInitializer(MinioClient client, @Value("${minio.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return;
        try {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException e) {
            // Another service instance may have created this bucket after our check.
            if (!"BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) throw e;
        }
    }
}
