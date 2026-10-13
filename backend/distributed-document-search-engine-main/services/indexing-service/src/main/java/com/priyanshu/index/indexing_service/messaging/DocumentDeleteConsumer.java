package com.priyanshu.index.indexing_service.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.priyanshu.documents.common.events.DocumentDeletedEvent;
import com.priyanshu.index.indexing_service.service.Constants;
import com.priyanshu.index.indexing_service.service.SearchCacheInvalidator;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentDeleteConsumer {

    private final ElasticsearchClient client ;
    private final SearchCacheInvalidator searchCacheInvalidator;

    public DocumentDeleteConsumer(
            ElasticsearchClient client,
            SearchCacheInvalidator searchCacheInvalidator) {
        this.client = client;
        this.searchCacheInvalidator = searchCacheInvalidator;
    }

    @KafkaListener(topics = "document_deleted", groupId = "search-indexer-delete")
    public void consume(DocumentDeletedEvent event, Acknowledgment ack) throws Exception {

        try {
            client.delete(d -> d
                .index(Constants.INDEX_NAME)
                .id(event.documentId())
                .refresh(Refresh.True)
            );
            searchCacheInvalidator.invalidate();
            ack.acknowledge();

            log.info("Deleted document {} from ES", event.documentId());

        } catch (Exception e) {
            log.error("Failed to delete document {} from ES", event.documentId(), e);
            throw e; // let Kafka retry
        }
    }
}
