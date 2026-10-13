package com.priyanshu.documents.document_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "upload_requests",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_upload_requests",
            columnNames = "idempotency_key"
        )
    }
)
public class UploadRequest {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UploadRequest() {}

    public UploadRequest(String key, UUID documentId) {
        this.idempotencyKey = key;
        this.documentId = documentId;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getDocumentId() { return documentId; }
}

