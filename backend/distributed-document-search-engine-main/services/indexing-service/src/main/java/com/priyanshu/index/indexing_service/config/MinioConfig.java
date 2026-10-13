package com.priyanshu.index.indexing_service.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
        @Value("${minio.url}") String url,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey
    ) {
        try {
            log.info("Creating MinIO client with endpoint: {}", url);
            MinioClient client = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .build();
            log.info("MinIO client created successfully");
            return client;
        } catch (Exception e) {
            log.error("Failed to create MinIO client: {}", e.getMessage(), e);
            throw new RuntimeException("MinIO client creation failed", e);
        }
    }
}
