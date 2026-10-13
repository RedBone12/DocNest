package com.priyanshu.index.indexing_service.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        try {
            log.info("Creating Elasticsearch client");

            ObjectMapper objectMapper = JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .build();

            RestClient restClient = RestClient.builder(
                    new HttpHost("localhost", 9200)
            ).build();

            ElasticsearchTransport transport =
                    new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

            ElasticsearchClient client = new ElasticsearchClient(transport);
            log.info("Elasticsearch client created successfully");
            return client;
        } catch (Exception e) {
            log.error("Failed to create Elasticsearch client: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch client creation failed", e);
        }
    }
}
