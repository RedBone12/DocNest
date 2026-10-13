package com.priyanshu.documents.document_service.controller;

import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.documents.document_service.dto.DocumentListItem;
import com.priyanshu.documents.document_service.dto.AiMetadataRequest;
import com.priyanshu.documents.document_service.dto.DocumentStatusResponse;
import com.priyanshu.documents.document_service.dto.UploadDocResponse;
import com.priyanshu.documents.document_service.entity.Document;
import com.priyanshu.documents.document_service.entity.DocumentStatus;
import com.priyanshu.documents.document_service.exception.DocumentServiceException;
import com.priyanshu.documents.document_service.repository.DocumentRepository;
import com.priyanshu.documents.document_service.service.DocumentService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import com.priyanshu.documents.document_service.dto.IndexingFailureRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/documents")
@Validated
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService service;
    private final DocumentRepository repository;

    public DocumentController(DocumentService service, DocumentRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadDocResponse> upload(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam @NotNull MultipartFile file,
            @RequestParam @NotBlank @Size(max = 255) String title,
            @RequestParam @NotBlank @Size(max = 1000) String description,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "false") boolean enableAiAnalysis,
            Authentication auth
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header required");
        }
        String userId = auth.getName();
        logger.info("Document upload request by user: {}, file: {}, size: {} bytes",
                   userId, file.getOriginalFilename(), file.getSize());

        try {
            List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : List.of();

            if (file.isEmpty()) {
                logger.warn("Empty file upload attempt by user: {}", userId);
                throw new DocumentServiceException("File cannot be empty");
            }

            if (file.getSize() > 50 * 1024 * 1024) { // 50MB limit
                logger.warn("File size exceeded by user: {}, size: {} bytes", userId, file.getSize());
                throw new DocumentServiceException("File size exceeds maximum allowed limit of 50MB");
            }

            UUID documentId = service.upload(idempotencyKey, file, title, description, tagList, userId, enableAiAnalysis);

            UploadDocResponse response = new UploadDocResponse(
                documentId,
                "UPLOADED",
                "Document uploaded successfully"
            );

            logger.info("Document uploaded successfully: {} by user: {}", response.getDocumentId(), userId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to upload document for user: {}", userId, e);
            throw new DocumentServiceException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id, Authentication auth) {
        String userId = auth.getName();
        logger.info("Document download request: {} by user: {}", id, userId);

        try {
            Document doc = service.getDocument(id, userId);
            InputStream stream = service.downloadFile(doc.getStoragePath());

            InputStreamResource resource = new InputStreamResource(stream);

            logger.info("Document downloaded successfully: {} by user: {}", id, userId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(doc.getContentType()))
                    .body(resource);

        } catch (Exception e) {
            logger.error("Failed to download document: {} for user: {}", id, userId, e);
            throw new DocumentServiceException("Failed to download document: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentStatusResponse> getStatus(@PathVariable UUID id, Authentication auth) {
        String userId = auth.getName();
        logger.debug("Status request for document: {} by user: {}", id, userId);

        try {
            DocumentStatus status = service.getDocumentStatus(id, userId);
            DocumentStatusResponse response = new DocumentStatusResponse(id, status);

            logger.debug("Status retrieved for document: {} - status: {}", id, status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get status for document: {} by user: {}", id, userId, e);
            throw e; // Let global exception handler deal with it
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestParam DocumentStatus status,
            Authentication auth
    ) {
        String userId = auth.getName();
        boolean isService = auth.getAuthorities().stream()
                .anyMatch(authority -> "SERVICE".equals(authority.getAuthority()));

        logger.info("Status update request for document: {} to status: {} by user: {} (service: {})",
                   id, status, userId, isService);

        try {
            service.updateStatus(id, status, userId, isService);
            logger.info("Status updated successfully for document: {} to {}", id, status);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Failed to update status for document: {} by user: {}", id, userId, e);
            throw e; // Let global exception handler deal with it
        }
    }

    @PutMapping("/{id}/ai-metadata")
    public ResponseEntity<Void> updateAiMetadata(
            @PathVariable UUID id,
            @RequestBody AiMetadataRequest request,
            Authentication auth
    ) throws AccessDeniedException {
        boolean isService = auth.getAuthorities().stream()
                .anyMatch(authority -> "SERVICE".equals(authority.getAuthority()));

        if (!isService) {
            throw new AccessDeniedException("Service authentication required");
        }

        service.updateAiMetadata(id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/indexing-failure")
    public ResponseEntity<Void> updateIndexingFailure(
            @PathVariable UUID id, @Valid @RequestBody IndexingFailureRequest request,
            Authentication auth) throws AccessDeniedException {
        if (auth.getAuthorities().stream().noneMatch(a -> "SERVICE".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Service authentication required");
        }
        service.updateIndexingFailure(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ai-analysis")
    public ResponseEntity<Void> requestAiAnalysis(
            @PathVariable UUID id,
            Authentication auth
    ) {
        service.requestAiAnalysis(id, auth.getName());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/documents/{id}")
    public Optional<Document> get(@PathVariable UUID id, Authentication auth) {
        String userId = auth.getName();
        logger.debug("Document retrieval request: {} by user: {}", id, userId);

        try {
            Optional<Document> document = repository.findByIdAndOwnerId(id, userId);
            if (document.isPresent()) {
                logger.debug("Document found: {} for user: {}", id, userId);
            } else {
                logger.warn("Document not found: {} for user: {}", id, userId);
            }
            return document;

        } catch (Exception e) {
            logger.error("Failed to retrieve document: {} for user: {}", id, userId, e);
            throw new DocumentServiceException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            Authentication authentication
    ) throws AccessDeniedException {
        String userId = authentication.getName();

        service.deleteDocument(id, userId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<DocumentListItem> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            Authentication auth
    ) {
        return service.listDocuments(
            auth.getName(),
            page,
            size,
            sort,
            order
        );
    }

}

