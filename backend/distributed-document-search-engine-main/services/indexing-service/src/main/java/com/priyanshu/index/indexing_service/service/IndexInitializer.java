package com.priyanshu.index.indexing_service.service;

import java.io.StringReader;

import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IndexInitializer {

    private final ElasticsearchClient client;

    public IndexInitializer(ElasticsearchClient client) {
        this.client = client;
    }

    @PostConstruct
    public void createIndex() {
        String indexName = Constants.INDEX_NAME;

        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                client.indices().create(c -> c
                    .index(indexName)
                    .withJson(new StringReader(Constants.INDEX_MAPPING_JSON))
                );

                log.info("Index {} created successfully", indexName);
            } else {
                log.info("Index {} already exists", indexName);
            }

            migratePreviousIndexIfNeeded();
        } catch (Exception e) {
            log.error("Failed to create index {}: {}", indexName, e.getMessage(), e);
            throw new RuntimeException("Index initialization failed", e);
        }
    }

    private void migratePreviousIndexIfNeeded() throws Exception {
        boolean previousExists = client.indices()
                .exists(e -> e.index(Constants.PREVIOUS_INDEX_NAME))
                .value();

        if (!previousExists) {
            log.info("Previous index {} does not exist; migration skipped",
                    Constants.PREVIOUS_INDEX_NAME);
            return;
        }

        long targetDocumentCount = client.count(c -> c.index(Constants.INDEX_NAME)).count();
        if (targetDocumentCount > 0) {
            log.info("Index {} already contains {} documents; migration skipped",
                    Constants.INDEX_NAME, targetDocumentCount);
            return;
        }

        var response = client.reindex(r -> r
                .source(s -> s.index(Constants.PREVIOUS_INDEX_NAME))
                .dest(d -> d.index(Constants.INDEX_NAME))
                .refresh(true)
        );

        log.info("Migrated {} documents from {} to {}",
                response.created(), Constants.PREVIOUS_INDEX_NAME, Constants.INDEX_NAME);
    }
}

