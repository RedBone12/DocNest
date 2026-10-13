package com.priyanshu.documents.document_service.dto;

import java.util.UUID;

public class UploadDocResponse {
    private UUID documentId;
    private String status;
    private String message;

    public UploadDocResponse(UUID documentId, String status, String message) {
        this.documentId = documentId;
        this.status = status;
        this.message = message;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
    
}
