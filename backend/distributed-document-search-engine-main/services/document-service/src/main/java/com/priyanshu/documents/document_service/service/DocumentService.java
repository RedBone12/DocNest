package com.priyanshu.documents.document_service.service;

import java.io.InputStream;
import java.time.Instant;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.documents.document_service.dto.DocumentListItem;
import com.priyanshu.documents.document_service.dto.AiMetadataRequest;
import com.priyanshu.documents.document_service.dto.IndexingFailureRequest;
import com.priyanshu.documents.document_service.entity.Document;
import com.priyanshu.documents.document_service.entity.DocumentStatus;
import com.priyanshu.documents.document_service.entity.UploadRequest;
import com.priyanshu.documents.document_service.exception.DocumentServiceException;
import com.priyanshu.documents.document_service.exception.NotFoundException;
import com.priyanshu.documents.document_service.repository.DocumentRepository;
import com.priyanshu.documents.document_service.repository.UploadRequestRepository;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import com.priyanshu.documents.common.events.DocumentUploadedEvent;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final MinioClient minioClient;
    private final DocumentEventProducer producer;
    private final UploadRequestRepository uploadRequestRepository;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentService(DocumentRepository repository,
            MinioClient minioClient,
            DocumentEventProducer producer,
            UploadRequestRepository uploadRequestRepository) {
        this.repository = repository;
        this.minioClient = minioClient;
        this.producer = producer;
        this.uploadRequestRepository = uploadRequestRepository;
    }

    @Transactional
    public UUID upload(String idempotencyKey,MultipartFile file, String title, String description, List<String> tags, String userId, boolean enableAiAnalysis) {
        logger.info("Starting document upload for user: {}, file: {}, size: {} bytes",
                   userId, file.getOriginalFilename(), file.getSize());

        Optional<UploadRequest> existing =  uploadRequestRepository.findById(idempotencyKey);

        if (existing.isPresent()) {
            logger.info("Idempotent upload detected for key: {}, returning existing document ID: {}",
                        idempotencyKey, existing.get().getDocumentId());
            return existing.get().getDocumentId();
        }

        UUID documentId = UUID.randomUUID();
        String objectName = documentId + "-" + file.getOriginalFilename();

        try {
            
            uploadRequestRepository.save(new UploadRequest(idempotencyKey, documentId));

            logger.debug("Uploading file to MinIO: {}", objectName);
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            logger.debug("File uploaded to MinIO successfully: {}", objectName);

            logger.debug("Saving document metadata to database");
            Document doc = new Document();
            doc.setId(documentId);
            doc.setTitle(title);
            doc.setDescription(description);
            doc.setFileName(file.getOriginalFilename());
            doc.setFileSize(file.getSize());
            doc.setContentType(file.getContentType());
            doc.setStoragePath(objectName);
            doc.setStatus(DocumentStatus.UPLOADED);
            doc.setOwnerId(userId);
            doc.setAiStatus(enableAiAnalysis ? "PENDING" : "DISABLED");
            doc.setAiMessage(enableAiAnalysis ? null : "AI analysis was not requested");

            Document saved = repository.save(doc);
            logger.debug("Document metadata saved with ID: {}", saved.getId());

            logger.debug("Publishing document uploaded event for: {}", documentId);
            DocumentUploadedEvent event = new DocumentUploadedEvent(
                documentId.toString(),
                objectName,
                title,
                description,
                userId,
                file.getSize(),
                DocumentStatus.UPLOADED.name(),
                0,
                enableAiAnalysis
            );

            producer.publishDocumentUploaded(event);
            logger.info("Document uploaded successfully: {} for user: {}", documentId, userId);

            return saved.getId();

        } catch (MinioException e) {
            logger.error("MinIO error during upload for document: {}", documentId, e);
            throw new DocumentServiceException("Failed to upload file to storage: " + e.getMessage(), e);
        } catch (DataIntegrityViolationException e) {
            return uploadRequestRepository
                   .findById(idempotencyKey)
                   .get()
                   .getDocumentId();
        } catch (Exception e) {
            logger.error("Unexpected error during document upload: {}", documentId, e);
            throw new DocumentServiceException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    public Document getDocument(UUID documentId, String userId) {
        logger.debug("Retrieving document: {} for user: {}", documentId, userId);

        Document document = repository.findByIdAndOwnerId(documentId, userId)
                .orElseThrow(() -> {
                    logger.warn("Document not found: {} for user: {}", documentId, userId);
                    return new EntityNotFoundException("Document not found or access denied: " + documentId);
                });

        logger.debug("Document retrieved successfully: {} for user: {}", documentId, userId);
        return document;
    }

    public InputStream downloadFile(String storagePath) {
        logger.debug("Downloading file from MinIO: {}", storagePath);

        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(storagePath)
                            .build());
            logger.debug("File downloaded successfully from MinIO: {}", storagePath);
            return stream;

        } catch (MinioException e) {
            logger.error("MinIO error during download: {}", storagePath, e);
            throw new DocumentServiceException("Failed to download file from storage: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during file download: {}", storagePath, e);
            throw new DocumentServiceException("Failed to download file: " + e.getMessage(), e);
        }
    }

    public DocumentStatus getDocumentStatus(UUID documentId, String userId) {
        logger.debug("Getting status for document: {} by user: {}", documentId, userId);

        Document doc = repository.findByIdAndOwnerId(documentId, userId)
                .orElseThrow(() -> {
                    logger.warn("Document not found for status check: {} by user: {}", documentId, userId);
                    return new EntityNotFoundException("Document not found or access denied: " + documentId);
                });

        DocumentStatus status = doc.getStatus();
        logger.debug("Document status retrieved: {} - {}", documentId, status);
        return status;
    }

    @Transactional
    public void updateStatus(UUID documentId, DocumentStatus status, String userId, boolean isService) {
        logger.info("Updating status for document: {} to {} by user: {} (service: {})",
                documentId, status, userId, isService);

        try {
            if (!isService) {
                logger.debug("Verifying ownership for document: {} by user: {}", documentId, userId);
                repository.findByIdAndOwnerId(documentId, userId)
                        .orElseThrow(() -> {
                            logger.warn("Document not found or access denied for status update: {} by user: {}",
                                    documentId, userId);
                            return new EntityNotFoundException("Document not found or access denied: " + documentId);
                        });
            }

            int updated = repository.updateStatus(documentId, status);

            if (updated == 0) {
                logger.warn("No document found to update status: {}", documentId);
                throw new EntityNotFoundException("Document not found: " + documentId);
            }

            logger.info("Document status updated successfully: {} to {}", documentId, status);

        } catch (EntityNotFoundException e) {
            throw e; // Re-throw as is
        } catch (Exception e) {
            logger.error("Unexpected error during status update for document: {}", documentId, e);
            throw new DocumentServiceException("Failed to update document status: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteDocument(UUID documentId, String userId) throws AccessDeniedException {

        Document doc = repository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        if (!doc.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not owner of document");
        }

        String storagePath = doc.getStoragePath();

        repository.delete(doc);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(storagePath)
                            .build());
        } catch (Exception e) {
            logger.error("MinIO delete failed for {}", documentId, e);
        }

        producer.publishDocumentDeleted(documentId.toString());
    }

    public Page<DocumentListItem> listDocuments(
            String ownerId,
            int page,
            int size,
            String sort,
            String order) {

        Sort.Direction dir = order.equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(dir, sort));

        return repository.findByOwnerId(ownerId, pageable)
                .map(doc -> new DocumentListItem(
                        doc.getId().toString(),
                        doc.getTitle(),
                        doc.getDescription(),
                        doc.getStatus().name(),
                        doc.getFileSize(),
                        doc.getCreatedAt(),
                        doc.getAiSummary(),
                        doc.getAiTags(),
                        doc.getAiStatus(),
                        doc.getAiMessage(),
                        doc.getFailureCode(),
                        doc.getFailureMessage(),
                        doc.getFailureCount(),
                        doc.getFailedAt()));
    }

    @Transactional
    public void updateAiMetadata(UUID documentId, AiMetadataRequest request) {
        Document doc = repository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        doc.setAiSummary(request.summary());
        doc.setAiTags(request.tags());
        doc.setAiStatus(request.status());
        doc.setAiMessage(request.message());
        repository.save(doc);
    }

    @Transactional
    public void updateIndexingFailure(UUID documentId, IndexingFailureRequest request) {
        Document doc = repository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        doc.setStatus(DocumentStatus.FAILED);
        doc.setFailureCode(request.code());
        doc.setFailureMessage(request.message());
        doc.setFailureCount(request.failureCount());
        doc.setFailedAt(Instant.now());
        if ("PENDING".equals(doc.getAiStatus())) {
            doc.setAiStatus("FAILED");
            doc.setAiMessage("AI analysis could not finish because document processing failed.");
        }
        repository.save(doc);
    }

    @Transactional
    public void requestAiAnalysis(UUID documentId, String userId) {
        Document doc = getDocument(documentId, userId);
        doc.setAiStatus("PENDING");
        doc.setAiMessage(null);
        repository.save(doc);

        producer.publishDocumentUploaded(new DocumentUploadedEvent(
                doc.getId().toString(),
                doc.getStoragePath(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getOwnerId(),
                doc.getFileSize(),
                doc.getStatus().name(),
                0,
                true));
    }

}
