package com.priyanshu.documents.document_service.service;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.priyanshu.documents.common.events.DocumentUploadedEvent;
import com.priyanshu.documents.common.events.DocumentDeletedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(DocumentEventProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;
    private final KafkaTemplate<String, DocumentDeletedEvent> kafkaTemplateDeleted;

    public DocumentEventProducer(KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate,
                                 KafkaTemplate<String, DocumentDeletedEvent> kafkaTemplateDeleted) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTemplateDeleted = kafkaTemplateDeleted;
    }

    public void publishDocumentUploaded(DocumentUploadedEvent event) {
        try {
            kafkaTemplate.send("document_uploaded", event.documentId(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.info("Published DocumentUploadedEvent: {}", event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing document uploaded event", e);
        } catch (Exception e) {
            logger.error("Failed to publish DocumentUploadedEvent: {}", event, e);
            throw new RuntimeException("Failed to publish document uploaded event", e);
        }
    }

    public void publishDocumentDeleted(String documentId) {
        try {
            kafkaTemplateDeleted.send("document_deleted", documentId, new DocumentDeletedEvent(documentId))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.info("Published DocumentDeletedEvent for document: {}", documentId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing document deleted event", e);
        } catch (Exception e) {
            logger.error("Failed to publish DocumentDeletedEvent: {}", documentId, e);
            throw new RuntimeException("Failed to publish document deleted event", e);
        }
    }
}
