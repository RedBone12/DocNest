package com.priyanshu.documents.common.events;

public record DocumentUploadedEvent(
        String documentId,
        String storagePath,
        String title,
        String description,
        String ownerId,
        Long fileSize,
        String status,
        int retryCount,
        Boolean enableAiAnalysis
) {}
