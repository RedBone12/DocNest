package com.priyanshu.documents.search_service.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticSearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ObjectMapper objectMapper) {
        logger.info("Initializing Elasticsearch client connection to localhost:9200");

        try {
            RestClient restClient = RestClient.builder(
                    new HttpHost("localhost", 9200)
            ).build();

            ElasticsearchTransport transport =
                    new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

            ElasticsearchClient client = new ElasticsearchClient(transport);

            client.info();
            logger.info("Elasticsearch client initialized successfully");

            return client;
        } catch (Exception e) {
            logger.error("Failed to initialize Elasticsearch client: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch client initialization failed", e);
        }
    }
}
