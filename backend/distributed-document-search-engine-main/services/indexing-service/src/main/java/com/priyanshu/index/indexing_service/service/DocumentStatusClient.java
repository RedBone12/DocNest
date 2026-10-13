package com.priyanshu.index.indexing_service.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;
import com.priyanshu.index.indexing_service.dto.AiAnalysis;

@Service
@Slf4j
public class DocumentStatusClient {

    private IndexingService indexingService;

    public DocumentStatusClient(IndexingService indexingService) {
        this.indexingService = indexingService;
    }
    private final RestTemplate restTemplate = new RestTemplate();

    public void updateStatus(String documentId, String status) {
        String url = "http://localhost:8081/documents/" + documentId + "/status?status=" + status;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(indexingService.generateServiceToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("Updated status for document {} to {}", documentId, status);
        } catch (RestClientException e) {
            log.error("Failed to update status for document {} to {}: {}", documentId, status, e.getMessage(), e);
            throw new RuntimeException("Status update failed for document " + documentId, e);
        }
    }

    public void updateAiMetadata(String documentId, AiAnalysis analysis) {
        String url = "http://localhost:8081/documents/" + documentId + "/ai-metadata";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(indexingService.generateServiceToken());

            HttpEntity<AiAnalysis> entity = new HttpEntity<>(analysis, headers);
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("Updated AI metadata for document {} with status {}",
                    documentId, analysis.status());
        } catch (RestClientException e) {
            throw new RuntimeException("AI metadata update failed for document " + documentId, e);
        }
    }

    public void updateFailure(String documentId, String code, String message, int failureCount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(indexingService.generateServiceToken());
        var body = java.util.Map.of("code", code, "message", message, "failureCount", failureCount);
        restTemplate.exchange("http://localhost:8081/documents/" + documentId + "/indexing-failure",
                HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }
}

