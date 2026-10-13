package com.priyanshu.index.indexing_service.dto;

import java.util.List;

public class SearchDocument {
    public String documentId;
    public String title;
    public String description;
    public String content;
    public String ownerId;
    public Long fileSize;
    public String status;
    public String createdAt;
    public String aiSummary;
    public List<String> aiTags;
    public String aiStatus;
    public String aiMessage;

    public SearchDocument(String documentId, String title, String description, String content, String ownerId,
            Long fileSize, String status, String createdAt, String aiSummary, List<String> aiTags, String aiStatus,
            String aiMessage) {
        this.documentId = documentId;
        this.title = title;
        this.description = description;
        this.content = content;
        this.ownerId = ownerId;
        this.fileSize = fileSize;
        this.status = status;
        this.createdAt = createdAt;
        this.aiSummary = aiSummary;
        this.aiTags = aiTags;
        this.aiStatus = aiStatus;
        this.aiMessage = aiMessage;
    }
}

