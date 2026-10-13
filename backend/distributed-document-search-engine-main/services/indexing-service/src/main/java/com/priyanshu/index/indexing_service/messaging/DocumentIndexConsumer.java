package com.priyanshu.index.indexing_service.messaging;

import java.io.InputStream;
import java.time.Instant;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.priyanshu.documents.common.events.DocumentUploadedEvent;
import com.priyanshu.index.indexing_service.dto.SearchDocument;
import com.priyanshu.index.indexing_service.dto.AiAnalysis;
import com.priyanshu.index.indexing_service.service.DocumentStatusClient;
import com.priyanshu.index.indexing_service.service.AiDocumentAnalyzer;
import com.priyanshu.index.indexing_service.service.IndexingService;
import com.priyanshu.index.indexing_service.service.SearchCacheInvalidator;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentIndexConsumer {

    private static final int MAX_RETRIES = 3;

    private final DocumentStatusClient statusClient;
    private final IndexingService indexingService;
    private final KafkaFailureProducer failureProducer;
    private final SearchCacheInvalidator searchCacheInvalidator;
    private final AiDocumentAnalyzer aiDocumentAnalyzer;

    public DocumentIndexConsumer(
            DocumentStatusClient statusClient,
            IndexingService indexingService,
            KafkaFailureProducer failureProducer,
            SearchCacheInvalidator searchCacheInvalidator,
            AiDocumentAnalyzer aiDocumentAnalyzer) {
        this.statusClient = statusClient;
        this.indexingService = indexingService;
        this.failureProducer = failureProducer;
        this.searchCacheInvalidator = searchCacheInvalidator;
        this.aiDocumentAnalyzer = aiDocumentAnalyzer;
    }

    @KafkaListener(topics = "document_uploaded", containerFactory = "kafkaListenerContainerFactory")
    public void consume(DocumentUploadedEvent event, Acknowledgment ack) throws Exception {
        log.info("Received message from document_uploaded topic");
        handle(event, ack);
    }

    @KafkaListener(topics = "document_uploaded_retry", containerFactory = "kafkaListenerContainerFactory")
    public void retry(DocumentUploadedEvent event, Acknowledgment ack) throws Exception {
        log.info("Received message from document_uploaded_retry topic");
        handle(event, ack);
    }

    private void handle(DocumentUploadedEvent event, Acknowledgment ack) throws Exception {
        try {
            process(event);
            ack.acknowledge(); // commit offset only on success
        } catch (Exception ex) {
            log.error("Indexing failed for document {}", event.documentId(), ex);
            handleFailure(event);
            ack.acknowledge();
        }
    }

    private void handleFailure(DocumentUploadedEvent event) throws Exception {
        int retries = event.retryCount() + 1;

        DocumentUploadedEvent failedEvent = new DocumentUploadedEvent(
                event.documentId(),
                event.storagePath(),
                event.title(),
                event.description(),
                event.ownerId(),
                event.fileSize(),
                event.status(),
                retries,
                event.enableAiAnalysis()
        );

        if (retries >= MAX_RETRIES) {
            log.error("Sending document {} to DLQ after {} attempts", event.documentId(), retries);
            failureProducer.sendToDlq(failedEvent);

            statusClient.updateFailure(event.documentId(), "RETRY_EXHAUSTED",
                    "Document processing failed repeatedly. Please contact support or upload a corrected file.", retries);

        } else {
            log.warn("Retrying document {} (attempt {})", event.documentId(), retries);
            failureProducer.sendToRetry(failedEvent);
        }
    }

    private void process(DocumentUploadedEvent event) throws Exception {

        log.info("Processing document {}", event.documentId());

        statusClient.updateStatus(event.documentId(), "INDEXING");

        var stat = indexingService.validateFileContent(event);
        if (stat.size() == 0) {
            log.warn("Empty file for document {}", event.documentId());
            statusClient.updateFailure(event.documentId(), "EMPTY_FILE",
                    "The stored file is empty. Upload a non-empty file.", event.retryCount() + 1);
            return;
        }

        String content;
        try (InputStream fileStream = indexingService.fetchFile(event.storagePath())) {
            content = indexingService.extractText(fileStream);
        }

        if (content == null || content.trim().isEmpty()) {
            log.warn("No extractable content for document {}", event.documentId());
            statusClient.updateFailure(event.documentId(), "NO_EXTRACTABLE_TEXT",
                    "No readable text was found. Scanned documents may require OCR before upload.", event.retryCount() + 1);
            return;
        }

        AiAnalysis aiAnalysis = Boolean.TRUE.equals(event.enableAiAnalysis())
                ? aiDocumentAnalyzer.analyze(event.title(), content)
                : AiAnalysis.disabled("AI analysis was not requested");
        statusClient.updateAiMetadata(event.documentId(), aiAnalysis);

        SearchDocument searchDoc = new SearchDocument(
                event.documentId(),
                event.title(),
                event.description(),
                content,
                event.ownerId(),
                event.fileSize(),
                "READY",
                Instant.now().toString(),
                aiAnalysis.summary(),
                aiAnalysis.tags(),
                aiAnalysis.status(),
                aiAnalysis.message()
        );

        indexingService.indexDocument(event.documentId(), searchDoc); // idempotent
        searchCacheInvalidator.invalidate();

        statusClient.updateStatus(event.documentId(), "READY");

        log.info("Successfully indexed document {}", event.documentId());
    }
}
