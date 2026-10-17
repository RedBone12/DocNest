package com.priyanshu.documents.document_service.entity;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 64, unique = true)
    private String resetTokenHash;

    private Instant resetExpiresAt;

    protected User() {
        // JPA 需要无参构造器
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public String getResetTokenHash() { return resetTokenHash; }
    public Instant getResetExpiresAt() { return resetExpiresAt; }
    public void setPasswordReset(String hash, Instant expiresAt) {
        this.resetTokenHash = hash;
        this.resetExpiresAt = expiresAt;
    }
}
