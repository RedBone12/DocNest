package com.priyanshu.documents.document_service.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "auth_sessions_user_idx", columnList = "user_id"))
public class AuthSession {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private Instant expiresAt;

    protected AuthSession() {}
    public AuthSession(String tokenHash, UUID userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
