package com.priyanshu.documents.document_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.priyanshu.documents.document_service.entity.Document;
import com.priyanshu.documents.document_service.entity.DocumentStatus;

@EnableJpaRepositories
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    static final Logger logger = LoggerFactory.getLogger(DocumentRepository.class);
    
    Optional<Document> findById(UUID id);

    @Modifying
    @Query("update Document d set d.status = :status where d.id = :id")
    int updateStatus(UUID id, DocumentStatus status);

    // New methods for ownerId-based queries
    Optional<Document> findByIdAndOwnerId(UUID id, String ownerId);
    List<Document> findAllByOwnerId(String ownerId);

    // Default logging for custom queries (Java 8+ interface default methods)
    default Optional<Document> safeFindByIdAndOwnerId(UUID id, String ownerId) {
        try {
            Optional<Document> result = findByIdAndOwnerId(id, ownerId);
            logger.debug("safeFindByIdAndOwnerId called for id: {}, ownerId: {}", id, ownerId);
            return result;
        } catch (Exception e) {
            logger.error("Error in safeFindByIdAndOwnerId for id: {}, ownerId: {}", id, ownerId, e);
            throw e;
        }
    }

    Page<Document> findByOwnerId(
        String ownerId,
        Pageable pageable
    );

}