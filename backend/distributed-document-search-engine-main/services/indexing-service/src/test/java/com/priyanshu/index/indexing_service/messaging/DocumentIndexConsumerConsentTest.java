package com.priyanshu.index.indexing_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import com.priyanshu.documents.common.events.DocumentUploadedEvent;
import com.priyanshu.index.indexing_service.dto.AiAnalysis;
import com.priyanshu.index.indexing_service.dto.SearchDocument;
import com.priyanshu.index.indexing_service.service.AiDocumentAnalyzer;
import com.priyanshu.index.indexing_service.service.DocumentStatusClient;
import com.priyanshu.index.indexing_service.service.IndexingService;
import com.priyanshu.index.indexing_service.service.SearchCacheInvalidator;

import io.minio.StatObjectResponse;
import org.mockito.ArgumentCaptor;

class DocumentIndexConsumerConsentTest {

    @Test
    void exhaustedRetriesPublishDlqAndPersistFailureBeforeAcknowledging() throws Exception {
        DocumentStatusClient status = mock(DocumentStatusClient.class);
        IndexingService indexing = mock(IndexingService.class);
        KafkaFailureProducer failures = mock(KafkaFailureProducer.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        DocumentUploadedEvent event = new DocumentUploadedEvent(
                "document-1", "object-1", "Title", "Description", "user-1", 10L, "UPLOADED", 2, false);
        when(indexing.validateFileContent(event)).thenThrow(new RuntimeException("storage unavailable"));
        new DocumentIndexConsumer(status, indexing, failures, mock(SearchCacheInvalidator.class),
                mock(AiDocumentAnalyzer.class)).consume(event, ack);
        var order = org.mockito.Mockito.inOrder(failures, status, ack);
        order.verify(failures).sendToDlq(any());
        order.verify(status).updateFailure(eq("document-1"), eq("RETRY_EXHAUSTED"), any(), eq(3));
        order.verify(ack).acknowledge();
    }

    @Test
    void failedStatusPersistenceDoesNotAcknowledgeTheMessage() throws Exception {
        DocumentStatusClient status = mock(DocumentStatusClient.class);
        IndexingService indexing = mock(IndexingService.class);
        KafkaFailureProducer failures = mock(KafkaFailureProducer.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        DocumentUploadedEvent event = new DocumentUploadedEvent(
                "document-1", "object-1", "Title", "Description", "user-1", 10L, "UPLOADED", 2, false);
        when(indexing.validateFileContent(event)).thenThrow(new RuntimeException("storage unavailable"));
        org.mockito.Mockito.doThrow(new RuntimeException("document service unavailable"))
                .when(status).updateFailure(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new DocumentIndexConsumer(status, indexing, failures, mock(SearchCacheInvalidator.class),
                        mock(AiDocumentAnalyzer.class)).consume(event, ack)).isInstanceOf(RuntimeException.class);
        org.mockito.Mockito.verifyNoInteractions(ack);
    }

    @Test
    void uploadWithoutConsentIsIndexedWithoutCallingAi() throws Exception {
        verifyAnalysisChoice(false, false);
    }

    @Test
    void legacyEventWithoutConsentIsIndexedWithoutCallingAi() throws Exception {
        verifyAnalysisChoice(null, false);
    }

    @Test
    void uploadWithConsentCallsAi() throws Exception {
        verifyAnalysisChoice(true, true);
    }

    private void verifyAnalysisChoice(Boolean consent, boolean shouldCallAi) throws Exception {
        DocumentStatusClient statusClient = mock(DocumentStatusClient.class);
        IndexingService indexingService = mock(IndexingService.class);
        KafkaFailureProducer failureProducer = mock(KafkaFailureProducer.class);
        SearchCacheInvalidator cacheInvalidator = mock(SearchCacheInvalidator.class);
        AiDocumentAnalyzer analyzer = mock(AiDocumentAnalyzer.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        StatObjectResponse stat = mock(StatObjectResponse.class);

        DocumentUploadedEvent event = new DocumentUploadedEvent(
                "document-1", "object-1", "Example", "Description", "user-1",
                10L, "UPLOADED", 0, consent);
        when(stat.size()).thenReturn(10L);
        when(indexingService.validateFileContent(event)).thenReturn(stat);
        when(indexingService.fetchFile("object-1"))
                .thenReturn(new ByteArrayInputStream("text".getBytes()));
        when(indexingService.extractText(any())).thenReturn("Document text");
        if (shouldCallAi) {
            when(analyzer.analyze("Example", "Document text"))
                    .thenReturn(AiAnalysis.completed("Summary", java.util.List.of("one", "two", "three")));
        }

        DocumentIndexConsumer consumer = new DocumentIndexConsumer(
                statusClient, indexingService, failureProducer, cacheInvalidator, analyzer);
        consumer.consume(event, acknowledgment);

        ArgumentCaptor<AiAnalysis> analysis = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(statusClient).updateAiMetadata(eq("document-1"), analysis.capture());
        ArgumentCaptor<SearchDocument> indexed = ArgumentCaptor.forClass(SearchDocument.class);
        verify(indexingService).indexDocument(eq("document-1"), indexed.capture());
        verify(acknowledgment).acknowledge();

        if (shouldCallAi) {
            verify(analyzer).analyze("Example", "Document text");
            assertThat(analysis.getValue().status()).isEqualTo("COMPLETED");
            assertThat(indexed.getValue().aiStatus).isEqualTo("COMPLETED");
        } else {
            verifyNoInteractions(analyzer);
            assertThat(analysis.getValue().status()).isEqualTo("DISABLED");
            assertThat(indexed.getValue().aiStatus).isEqualTo("DISABLED");
        }
    }
}
