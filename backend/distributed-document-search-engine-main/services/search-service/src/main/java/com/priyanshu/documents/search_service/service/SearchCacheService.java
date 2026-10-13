package com.priyanshu.documents.search_service.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priyanshu.documents.search_service.dto.SearchResponse;

@Service
public class SearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(SearchCacheService.class);

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String VERSION_KEY = "docnest:search:version";

    private final StringRedisTemplate redis;

    private final ObjectMapper mapper;

    public SearchCacheService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public String buildKey(
            String userId,
            String query,
            List<String> tags,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String normalizedTags = tags.stream()
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isBlank())
                .sorted()
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        return "docnest:search:v2:"
                + currentVersion() + ":"
                + encode(userId) + ":"
                + page + ":"
                + size + ":"
                + encode(normalizedQuery) + ":"
                + encode(normalizedTags) + ":"
                + (fromDate == null ? "-" : fromDate) + ":"
                + (toDate == null ? "-" : toDate);
    }

    private String currentVersion() {
        try {
            String version = redis.opsForValue().get(VERSION_KEY);
            return version == null ? "0" : version;
        } catch (Exception e) {
            log.warn("Search cache version read failed: {}", e.getMessage());
            return "0";
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public SearchResponse get(String key) {
        try {
            String json = redis.opsForValue().get(key);

            if (json == null) {
                log.info("Search cache miss");
                return null;
            }

            SearchResponse result = mapper.readValue(json, SearchResponse.class);

            log.info("Search cache hit");
            return result;
        } catch (Exception e) {
            log.warn(
                    "Search cache read failed; falling back to Elasticsearch: {}",
                    e.getMessage());
            return null;
        }
    }

    public void put(String key, SearchResponse response) {
        try {
            String json = mapper.writeValueAsString(response);

            redis.opsForValue().set(key, json, TTL);

            log.info("Search result cached for {} seconds", TTL.toSeconds());
        } catch (Exception e) {
            log.warn(
                    "Search cache write failed; returning Elasticsearch result: {}",
                    e.getMessage());
        }
    }
}
