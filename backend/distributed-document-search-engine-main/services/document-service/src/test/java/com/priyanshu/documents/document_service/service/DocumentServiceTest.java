package com.priyanshu.documents.document_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.file.AccessDeniedException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import com.priyanshu.documents.document_service.dto.AiMetadataRequest;
import com.priyanshu.documents.document_service.dto.IndexingFailureRequest;
import jakarta.persistence.EntityNotFoundException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.documents.common.events.DocumentUploadedEvent;
import com.priyanshu.documents.document_service.entity.Document;
import com.priyanshu.documents.document_service.entity.DocumentStatus;
import com.priyanshu.documents.document_service.entity.UploadRequest;
import com.priyanshu.documents.document_service.exception.NotFoundException;
import com.priyanshu.documents.document_service.exception.DocumentServiceException;
import com.priyanshu.documents.document_service.repository.DocumentRepository;
import com.priyanshu.documents.document_service.repository.UploadRequestRepository;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.InternalException;

class DocumentServiceTest {
    private DocumentRepository documents;
    private MinioClient storage;
    private DocumentEventProducer events;
    private UploadRequestRepository requests;
    private DocumentService subject;
    private Document ownedDocument;
    private UUID id;

    @BeforeEach
    void prepareDependencies() {
        documents = mock(DocumentRepository.class);
        storage = mock(MinioClient.class);
        events = mock(DocumentEventProducer.class);
        requests = mock(UploadRequestRepository.class);
        subject = new DocumentService(documents, storage, events, requests);
        ReflectionTestUtils.setField(subject, "bucket", "documents");
        id = UUID.randomUUID();
        ownedDocument = new Document();
        ownedDocument.setId(id);
        ownedDocument.setOwnerId("owner");
        ownedDocument.setTitle("Report");
        ownedDocument.setDescription("Description");
        ownedDocument.setStoragePath("private/report.pdf");
        ownedDocument.setFileSize(3L);
        ownedDocument.setStatus(DocumentStatus.READY);
    }

    private MockMultipartFile uploadFile() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    @Test
    void uploadWithoutConsentDisablesAiInMetadataAndEvent() {
        when(documents.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
        UUID result = subject.upload("key", uploadFile(), "Report", "Description", List.of(), "owner", false);
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(result);
        assertThat(saved.getValue().getAiStatus()).isEqualTo("DISABLED");
        assertThat(saved.getValue().getAiMessage()).isNotBlank();
        verify(events).publishDocumentUploaded(argThat(event ->
                event.documentId().equals(result.toString()) && Boolean.FALSE.equals(event.enableAiAnalysis())));
    }

    @Test
    void duplicateKeyConflictReturnsTheWinningDocumentWithoutUploading() {
        when(requests.findById("key")).thenReturn(Optional.empty(), Optional.of(new UploadRequest("key", id)));
        when(requests.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate key"));
        assertThat(subject.upload("key", uploadFile(), "Report", "Description", List.of(), "owner", false))
                .isEqualTo(id);
        verifyNoInteractions(storage, documents, events);
    }

    @Test
    void unexpectedUploadFailurePreservesCauseAndDoesNotPublish() throws Exception {
        IOException failure = new IOException("Cannot read file");
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getInputStream()).thenThrow(failure);
        assertThatThrownBy(() -> subject.upload("key", file, "Report", "Description", List.of(), "owner", false))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
        verifyNoInteractions(storage, documents, events);
    }

    @Test
    void databaseUploadFailureDoesNotPublishAnEvent() {
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(documents.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> subject.upload("key", uploadFile(), "Report", "Description", List.of(), "owner", false))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
        verifyNoInteractions(events);
    }

    @Test
    void uploadEventFailureIsReportedToTheCaller() {
        when(documents.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
        IllegalStateException failure = new IllegalStateException("Kafka unavailable");
        doThrow(failure).when(events).publishDocumentUploaded(any());
        assertThatThrownBy(() -> subject.upload("key", uploadFile(), "Report", "Description", List.of(), "owner", false))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
    }

    @Test
    void retrievesDocumentAndStatusUsingTheOwnerScopedQuery() {
        when(documents.findByIdAndOwnerId(id, "owner")).thenReturn(Optional.of(ownedDocument));
        assertThat(subject.getDocument(id, "owner")).isSameAs(ownedDocument);
        assertThat(subject.getDocumentStatus(id, "owner")).isEqualTo(DocumentStatus.READY);
        verify(documents, times(2)).findByIdAndOwnerId(id, "owner");
        verifyNoMoreInteractions(documents);
    }

    @Test
    void inaccessibleDocumentCannotBeReadOrHaveItsStatusRead() {
        assertThatThrownBy(() -> subject.getDocument(id, "other"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> subject.getDocumentStatus(id, "other"))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documents, times(2)).findByIdAndOwnerId(id, "other");
        verifyNoMoreInteractions(documents);
    }

    @Test
    void downloadsTheRequestedStorageObject() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(storage.getObject(any(GetObjectArgs.class))).thenReturn(response);
        assertThat(subject.downloadFile("private/report.pdf")).isSameAs(response);
        verify(storage).getObject(argThat(args -> args.bucket().equals("documents")
                && args.object().equals("private/report.pdf")));
    }

    @Test
    void downloadStorageFailurePreservesItsCause() throws Exception {
        InternalException failure = new InternalException("Unavailable", "Test failure");
        when(storage.getObject(any(GetObjectArgs.class))).thenThrow(failure);
        assertThatThrownBy(() -> subject.downloadFile("report.pdf"))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
    }

    @Test
    void unexpectedDownloadFailurePreservesItsCause() throws Exception {
        IOException failure = new IOException("Connection closed");
        when(storage.getObject(any(GetObjectArgs.class))).thenThrow(failure);
        assertThatThrownBy(() -> subject.downloadFile("report.pdf"))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
    }

    @Test
    void ownerCanUpdateDocumentStatus() {
        when(documents.findByIdAndOwnerId(id, "owner")).thenReturn(Optional.of(ownedDocument));
        when(documents.updateStatus(id, DocumentStatus.INDEXING)).thenReturn(1);
        subject.updateStatus(id, DocumentStatus.INDEXING, "owner", false);
        verify(documents).findByIdAndOwnerId(id, "owner");
        verify(documents).updateStatus(id, DocumentStatus.INDEXING);
    }

    @Test
    void statusUpdateRejectsNonOwnerBeforeWriting() {
        assertThatThrownBy(() -> subject.updateStatus(id, DocumentStatus.READY, "other", false))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documents).findByIdAndOwnerId(id, "other");
        verifyNoMoreInteractions(documents);
    }

    @Test
    void serviceCanUpdateStatusWithoutAnOwnerLookup() {
        when(documents.updateStatus(id, DocumentStatus.READY)).thenReturn(1);
        subject.updateStatus(id, DocumentStatus.READY, null, true);
        verify(documents).updateStatus(id, DocumentStatus.READY);
        verifyNoMoreInteractions(documents);
    }

    @Test
    void statusUpdateReportsMissingRows() {
        assertThatThrownBy(() -> subject.updateStatus(id, DocumentStatus.READY, null, true))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void statusDatabaseFailurePreservesItsCause() {
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(documents.updateStatus(id, DocumentStatus.READY)).thenThrow(failure);
        assertThatThrownBy(() -> subject.updateStatus(id, DocumentStatus.READY, null, true))
                .isInstanceOf(DocumentServiceException.class).hasCause(failure);
    }

    @Test
    void storageDeletionFailureStillPublishesTheSearchRemovalEvent() throws Exception {
        // Storage deletion is best effort; search removal must still be requested
        when(documents.findById(id)).thenReturn(Optional.of(ownedDocument));
        doThrow(new IOException("Storage unavailable")).when(storage).removeObject(any(RemoveObjectArgs.class));
        subject.deleteDocument(id, "owner");
        verify(documents).delete(ownedDocument);
        verify(events).publishDocumentDeleted(id.toString());
    }

    @Test
    void databaseDeletionFailureStopsStorageAndEventOperations() {
        when(documents.findById(id)).thenReturn(Optional.of(ownedDocument));
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(documents).delete(ownedDocument);
        assertThatThrownBy(() -> subject.deleteDocument(id, "owner")).isSameAs(failure);
        verifyNoInteractions(storage, events);
    }

    @Test
    void deletionEventFailureIsReported() {
        when(documents.findById(id)).thenReturn(Optional.of(ownedDocument));
        IllegalStateException failure = new IllegalStateException("Kafka unavailable");
        doThrow(failure).when(events).publishDocumentDeleted(id.toString());
        assertThatThrownBy(() -> subject.deleteDocument(id, "owner")).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "ASC", "desc"})
    void listsOnlyTheOwnersDocumentsWithPaginationAndMetadata(String order) {
        ownedDocument.setAiSummary("Summary");
        ownedDocument.setAiTags(List.of("finance"));
        ownedDocument.setAiStatus("FAILED");
        ownedDocument.setAiMessage("AI error");
        ownedDocument.setFailureCode("INDEX_ERROR");
        ownedDocument.setFailureMessage("Index unavailable");
        ownedDocument.setFailureCount(3);
        ownedDocument.setFailedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(documents.findByOwnerId(eq("owner"), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(ownedDocument), call.getArgument(1), 21));
        var result = subject.listDocuments("owner", 2, 10, "createdAt", order);
        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getSort()).isEqualTo(Sort.by(
                order.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt"));
        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.documentId()).isEqualTo(id.toString());
            assertThat(item.status()).isEqualTo("READY");
            assertThat(item).usingRecursiveComparison().comparingOnlyFields(
                    "title", "description", "fileSize", "createdAt", "aiSummary", "aiTags", "aiStatus",
                    "aiMessage", "failureCode", "failureMessage", "failureCount", "failedAt")
                    .isEqualTo(ownedDocument);
        });
        verify(documents).findByOwnerId(eq("owner"), any(Pageable.class));
    }

    @Test
    void listsAnEmptyPageWithoutInventingResults() {
        when(documents.findByOwnerId(eq("owner"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(subject.listDocuments("owner", 0, 10, "createdAt", "desc").getContent()).isEmpty();
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingTheDatabase() {
        assertThatThrownBy(() -> subject.listDocuments("owner", -1, 10, "createdAt", "asc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject.listDocuments("owner", 0, 0, "createdAt", "asc"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(documents);
    }

    @Test
    void savesAiMetadata() {
        when(documents.findById(id)).thenReturn(Optional.of(ownedDocument));
        subject.updateAiMetadata(id, new AiMetadataRequest("Summary", List.of("finance"), "READY", "Complete"));
        assertThat(ownedDocument.getAiSummary()).isEqualTo("Summary");
        assertThat(ownedDocument.getAiTags()).containsExactly("finance");
        assertThat(ownedDocument.getAiStatus()).isEqualTo("READY");
        assertThat(ownedDocument.getAiMessage()).isEqualTo("Complete");
        verify(documents).save(ownedDocument);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "DISABLED", "READY"})
    void indexingFailureRecordsDetailsAndOnlyFailsPendingAi(String aiStatus) {
        when(documents.findById(id)).thenReturn(Optional.of(ownedDocument));
        ownedDocument.setAiStatus(aiStatus);
        ownedDocument.setAiMessage("Existing message");
        Instant before = Instant.now();
        subject.updateIndexingFailure(id, new IndexingFailureRequest("INDEX_ERROR", "Unavailable", 3));
        assertThat(ownedDocument.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(ownedDocument.getFailureCode()).isEqualTo("INDEX_ERROR");
        assertThat(ownedDocument.getFailureMessage()).isEqualTo("Unavailable");
        assertThat(ownedDocument.getFailureCount()).isEqualTo(3);
        assertThat(ownedDocument.getFailedAt()).isBetween(before, Instant.now());
        assertThat(ownedDocument.getAiStatus()).isEqualTo(aiStatus.equals("PENDING") ? "FAILED" : aiStatus);
        if (aiStatus.equals("PENDING")) {
            assertThat(ownedDocument.getAiMessage()).contains("document processing failed");
        } else {
            assertThat(ownedDocument.getAiMessage()).isEqualTo("Existing message");
        }
        verify(documents).save(ownedDocument);
    }

    @Test
    void missingDocumentMetadataUpdatesDoNotSaveAnything() {
        assertThatThrownBy(() -> subject.updateAiMetadata(id, new AiMetadataRequest("Summary", List.of(), "READY", null)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> subject.updateIndexingFailure(id, new IndexingFailureRequest("ERROR", "Unavailable", 3)))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documents, times(2)).findById(id);
        verifyNoMoreInteractions(documents);
    }

    @Test
    void ownerCanRequestAiAnalysisAndClearThePreviousError() {
        when(documents.findByIdAndOwnerId(id, "owner")).thenReturn(Optional.of(ownedDocument));
        ownedDocument.setAiStatus("FAILED");
        ownedDocument.setAiMessage("Previous failure");
        subject.requestAiAnalysis(id, "owner");
        assertThat(ownedDocument.getAiStatus()).isEqualTo("PENDING");
        assertThat(ownedDocument.getAiMessage()).isNull();
        verify(documents).save(ownedDocument);
        verify(events).publishDocumentUploaded(new DocumentUploadedEvent(id.toString(),
                ownedDocument.getStoragePath(), "Report", "Description", "owner", 3L, "READY", 0, true));
    }

    @Test
    void nonOwnerCannotRequestAiAnalysis() {
        assertThatThrownBy(() -> subject.requestAiAnalysis(id, "other"))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documents).findByIdAndOwnerId(id, "other");
        verifyNoMoreInteractions(documents);
        verifyNoInteractions(events);
    }

    @Test
    void aiRequestDatabaseFailureDoesNotPublish() {
        when(documents.findByIdAndOwnerId(id, "owner")).thenReturn(Optional.of(ownedDocument));
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(documents.save(ownedDocument)).thenThrow(failure);
        assertThatThrownBy(() -> subject.requestAiAnalysis(id, "owner")).isSameAs(failure);
        verifyNoInteractions(events);
    }

    @Test
    void aiRequestEventFailureIsReportedToTheCaller() {
        when(documents.findByIdAndOwnerId(id, "owner")).thenReturn(Optional.of(ownedDocument));
        IllegalStateException failure = new IllegalStateException("Kafka unavailable");
        doThrow(failure).when(events).publishDocumentUploaded(any());
        assertThatThrownBy(() -> subject.requestAiAnalysis(id, "owner")).isSameAs(failure);
    }

    @Test
    void storageUploadFailureDoesNotSaveDocumentOrPublishEvent() throws Exception {
        // Arrange: Simulate a storage failure during a new upload
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        DocumentService service = new DocumentService(
                documentRepository, minioClient, eventProducer, uploadRequestRepository);
        ReflectionTestUtils.setField(service, "bucket", "documents");

        MultipartFile file = mock(MultipartFile.class);
        byte[] content = {1, 2, 3};
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(uploadRequestRepository.findById("failed-upload"))
                .thenReturn(Optional.empty());

        InternalException storageFailure = new InternalException("Storage unavailable", "Test storage failure");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(storageFailure);

        // Act and assert: Preserve the original cause and stop further processing
        assertThatThrownBy(() -> service.upload(
                "failed-upload", file, "Report", "Description", List.of(), "owner-123", false))
                .isInstanceOf(DocumentServiceException.class)
                .hasCause(storageFailure);

        verify(minioClient).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
        verifyNoInteractions(documentRepository, eventProducer);
    }

    @Test
    void rejectsDeletingMissingDocumentWithoutSideEffects() {
        // Arrange: No document exists for the requested ID
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        DocumentService service = new DocumentService(
                documentRepository, minioClient, eventProducer, uploadRequestRepository);

        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act and assert: Report the missing document before performing any deletion
        assertThatThrownBy(() -> service.deleteDocument(documentId, "owner-123"))
                .isInstanceOf(NotFoundException.class);

        verify(documentRepository).findById(documentId);
        verifyNoMoreInteractions(documentRepository);
        verifyNoInteractions(minioClient, eventProducer, uploadRequestRepository);
    }

    @Test
    void deletesOwnedDocumentFromDatabaseAndStorageAndPublishesEvent() throws Exception {
        // Arrange: Prepare an existing document owned by the requesting user
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        DocumentService service = new DocumentService(
                documentRepository, minioClient, eventProducer, uploadRequestRepository);
        ReflectionTestUtils.setField(service, "bucket", "documents");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setOwnerId("owner-123");
        document.setStoragePath(documentId + "-report.pdf");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        // Act: Delete the document as its owner
        service.deleteDocument(documentId, "owner-123");

        // Assert: Delete the correct record and storage object exactly once
        verify(documentRepository).findById(documentId);
        verify(documentRepository).delete(document);

        ArgumentCaptor<RemoveObjectArgs> storageCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(storageCaptor.capture());
        assertThat(storageCaptor.getValue().bucket()).isEqualTo("documents");
        assertThat(storageCaptor.getValue().object()).isEqualTo(document.getStoragePath());

        // Assert: Publish the deletion event for the same document
        verify(eventProducer).publishDocumentDeleted(documentId.toString());
        verifyNoMoreInteractions(documentRepository, minioClient, eventProducer);
        verifyNoInteractions(uploadRequestRepository);
    }

    @Test
    void rejectsDeletingAnotherUsersDocumentWithoutSideEffects() {
        // Arrange: The document belongs to a different user
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        DocumentService service = new DocumentService(
                documentRepository, minioClient, eventProducer, uploadRequestRepository);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setOwnerId("owner-123");
        document.setStoragePath("private-report.pdf");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        // Act and assert: Reject the request before any deletion or event publication
        assertThatThrownBy(() -> service.deleteDocument(documentId, "other-user"))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentRepository).findById(documentId);
        verifyNoMoreInteractions(documentRepository);
        verifyNoInteractions(minioClient, eventProducer, uploadRequestRepository);
    }

    @Test
    void returnsExistingDocumentIdWithoutUploadingAgain() {
        // Arrange: Prepare test data and mocked dependencies
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        MultipartFile file = mock(MultipartFile.class);

        DocumentService service = new DocumentService(
                documentRepository,
                minioClient,
                eventProducer,
                uploadRequestRepository);

        String idempotencyKey = "upload-key-1";
        UUID existingDocumentId = UUID.randomUUID();

        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getSize()).thenReturn(1024L);

        when(uploadRequestRepository.findById(idempotencyKey))
                .thenReturn(Optional.of(
                        new UploadRequest(idempotencyKey, existingDocumentId)));

        // Act: Call the method under test
        UUID actualDocumentId = service.upload(
                idempotencyKey,
                file,
                "Annual Report",
                "Company annual report",
                List.of("finance"),
                "user-123",
                false);

        // Assert: Verify the result and side effects
        assertThat(actualDocumentId).isEqualTo(existingDocumentId);

        verify(uploadRequestRepository).findById(idempotencyKey);
        verifyNoMoreInteractions(uploadRequestRepository);

        verifyNoInteractions(
                documentRepository,
                minioClient,
                eventProducer);
    }

    @Test
    @DisplayName("A new upload stores the file, saves its metadata, and publishes an event")
    void uploadsNewDocumentAndPublishesEvent() throws Exception {
        // Arrange: Prepare test data and mocked dependencies
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        DocumentEventProducer eventProducer = mock(DocumentEventProducer.class);
        UploadRequestRepository uploadRequestRepository = mock(UploadRequestRepository.class);
        MultipartFile file = mock(MultipartFile.class);

        DocumentService service = new DocumentService(
                documentRepository,
                minioClient,
                eventProducer,
                uploadRequestRepository);
        ReflectionTestUtils.setField(service, "bucket", "documents");

        String idempotencyKey = "upload-key-2";
        byte[] fileContent = "quarterly results".getBytes();

        when(uploadRequestRepository.findById(idempotencyKey))
                .thenReturn(Optional.empty());
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getSize()).thenReturn((long) fileContent.length);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Upload a document for the first time
        UUID documentId = service.upload(
                idempotencyKey,
                file,
                "Quarterly Report",
                "Results for the current quarter",
                List.of("finance", "quarterly"),
                "user-123",
                true);

        // Assert: Verify the stored file parameters
        ArgumentCaptor<PutObjectArgs> storageCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(storageCaptor.capture());

        PutObjectArgs storageRequest = storageCaptor.getValue();
        assertThat(storageRequest.bucket()).isEqualTo("documents");
        assertThat(storageRequest.object()).isEqualTo(documentId + "-report.pdf");

        // Assert: Verify the saved document metadata
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());

        Document savedDocument = documentCaptor.getValue();
        assertThat(savedDocument.getId()).isEqualTo(documentId);
        assertThat(savedDocument.getTitle()).isEqualTo("Quarterly Report");
        assertThat(savedDocument.getDescription()).isEqualTo("Results for the current quarter");
        assertThat(savedDocument.getFileName()).isEqualTo("report.pdf");
        assertThat(savedDocument.getFileSize()).isEqualTo((long) fileContent.length);
        assertThat(savedDocument.getContentType()).isEqualTo("application/pdf");
        assertThat(savedDocument.getOwnerId()).isEqualTo("user-123");
        assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(savedDocument.getAiStatus()).isEqualTo("PENDING");

        // Assert: Verify the event sent to Kafka
        ArgumentCaptor<DocumentUploadedEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventProducer).publishDocumentUploaded(eventCaptor.capture());

        DocumentUploadedEvent event = eventCaptor.getValue();
        assertThat(event.documentId()).isEqualTo(documentId.toString());
        assertThat(event.storagePath()).isEqualTo(documentId + "-report.pdf");
        assertThat(event.title()).isEqualTo("Quarterly Report");
        assertThat(event.ownerId()).isEqualTo("user-123");
        assertThat(event.fileSize()).isEqualTo((long) fileContent.length);
        assertThat(event.status()).isEqualTo(DocumentStatus.UPLOADED.name());
        assertThat(event.retryCount()).isZero();
        assertThat(event.enableAiAnalysis()).isTrue();

        verify(uploadRequestRepository).save(argThat(request ->
                request.getIdempotencyKey().equals(idempotencyKey)
                        && request.getDocumentId().equals(documentId)));
    }
}
