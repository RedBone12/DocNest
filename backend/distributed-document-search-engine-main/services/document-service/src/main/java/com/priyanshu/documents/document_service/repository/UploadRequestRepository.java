package com.priyanshu.documents.document_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.priyanshu.documents.document_service.entity.UploadRequest;

public interface UploadRequestRepository extends JpaRepository<UploadRequest, String> {
}

