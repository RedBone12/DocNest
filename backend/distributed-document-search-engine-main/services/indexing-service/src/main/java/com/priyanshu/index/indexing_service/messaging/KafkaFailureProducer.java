package com.priyanshu.index.indexing_service.messaging;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.priyanshu.documents.common.events.DocumentUploadedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KafkaFailureProducer {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;

    public KafkaFailureProducer(KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToRetry(DocumentUploadedEvent event) throws Exception {
        sendAndConfirm("document_uploaded_retry", event);
    }

    public void sendToDlq(DocumentUploadedEvent event) throws Exception {
        sendAndConfirm("document_uploaded_dlq", event);
    }

    private void sendAndConfirm(String topic, DocumentUploadedEvent event) throws Exception {
        try {
            kafkaTemplate.send(topic, event.documentId(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published document {} to {}", event.documentId(), topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
