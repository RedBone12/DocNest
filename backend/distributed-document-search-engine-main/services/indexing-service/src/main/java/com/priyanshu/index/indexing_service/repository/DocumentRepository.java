package com.priyanshu.index.indexing_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.priyanshu.index.indexing_service.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findById(UUID id);
}

