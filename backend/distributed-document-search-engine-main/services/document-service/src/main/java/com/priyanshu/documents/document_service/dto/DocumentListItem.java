package com.priyanshu.documents.document_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentListItem(
    String documentId, String title, String description, String status,
    Long fileSize, Instant createdAt, String aiSummary, List<String> aiTags,
    String aiStatus, String aiMessage, String failureCode, String failureMessage,
    Integer failureCount, Instant failedAt
) {
    public DocumentListItem(UUID documentId, String title, String description, String status,
            Long fileSize, Instant createdAt, String aiSummary, List<String> aiTags,
            String aiStatus, String aiMessage) {
        this(documentId.toString(), title, description, status, fileSize, createdAt,
                aiSummary, aiTags, aiStatus, aiMessage, null, null, null, null);
    }
}

