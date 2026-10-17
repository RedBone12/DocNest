package com.priyanshu.documents.document_service.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.priyanshu.documents.document_service.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    void deleteByUserId(UUID userId);
    void deleteByExpiresAtBefore(Instant now);
}
