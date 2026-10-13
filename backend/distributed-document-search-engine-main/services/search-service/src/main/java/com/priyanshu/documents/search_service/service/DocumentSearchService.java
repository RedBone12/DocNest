package com.priyanshu.documents.search_service.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.dto.SearchResultItem;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;

@Service
public class DocumentSearchService {

        private static final Logger logger = LoggerFactory.getLogger(DocumentSearchService.class);

        private final ElasticsearchClient client;
        private final SearchCacheService searchCache;

        public DocumentSearchService(ElasticsearchClient client, SearchCacheService searchCache) {
                this.client = client;
                this.searchCache = searchCache;
                logger.info("DocumentSearchService initialized");
        }

        public SearchResponse search(
                        String query,
                        List<String> tags,
                        LocalDate fromDate,
                        LocalDate toDate,
                        int page,
                        int size,
                        String userId) throws IOException {
                validateDateRange(fromDate, toDate);
                String cacheKey = searchCache.buildKey(userId, query, tags, fromDate, toDate, page, size);

                SearchResponse cached = searchCache.get(cacheKey);
                if (cached != null) return cached;
                logger.debug("Performing search - query: '{}', page: {}, size: {}, userId: {}", query, page, size,
                                userId);

                int from = page * size;
                BoolQuery.Builder boolQuery = new BoolQuery.Builder()
                                .filter(f -> f.term(t -> t.field("ownerId").value(userId)));

                if (!query.isBlank()) {
                        boolQuery.must(m -> m.multiMatch(mm -> mm
                                        .fields("title", "description", "content", "aiSummary")
                                        .operator(Operator.And)
                                        .query(query)));
                }

                tags.stream()
                                .map(String::trim)
                                .filter(tag -> !tag.isBlank())
                                .distinct()
                                .forEach(tag -> boolQuery.filter(f -> f.wildcard(w -> w.field("aiTags").value(tag)
                                                .caseInsensitive(true))));

                if (fromDate != null || toDate != null) {
                        boolQuery.filter(f -> f.range(r -> {
                                r.field("createdAt");
                                if (fromDate != null) r.gte(JsonData.of(fromDate + "T00:00:00Z"));
                                if (toDate != null) r.lte(JsonData.of(toDate + "T23:59:59.999Z"));
                                return r;
                        }));
                }

                long startTime = System.nanoTime();
                try {
                        var response = client.search(s -> s
                                        .index("documents_index_v4")
                                        .from(from)
                                        .size(size)
                                        .highlight(h -> h
                                                        .preTags("[[HIGHLIGHT]]")
                                                        .postTags("[[/HIGHLIGHT]]")
                                                        .fields("content", f -> f
                                                                        .fragmentSize(160)
                                                                        .numberOfFragments(2)))

                                        .query(q -> q.bool(boolQuery.build()))
                                        .sort(srt -> srt.field(f -> f.field("createdAt").order(SortOrder.Desc))),
                                        Map.class);

                        long searchTimeMs = (System.nanoTime() - startTime) / 1_000_000;

                        List<SearchResultItem> results = response.hits().hits().stream()
                                        .map(hit -> {
                                                Map<String, Object> src = hit.source();

                                                return new SearchResultItem(
                                                                hit.id(),
                                                                (String) src.get("title"),
                                                                (String) src.get("description"),
                                                                src.get("fileSize") instanceof Number n ? n.longValue()
                                                                                : null,
                                                                (String) src.get("status"),
                                                                String.valueOf(src.get("createdAt")),
                                                                hit.highlight().getOrDefault("content", List.of()),
                                                                (String) src.get("aiSummary"),
                                                                stringList(src.get("aiTags")),
                                                                (String) src.get("aiStatus"),
                                                                (String) src.get("aiMessage"));
                                        })
                                        .toList();

                        long totalHits = response.hits().total().value();
                        logger.info("Search completed - query: '{}', totalHits: {}, returned: {}, searchTime: {}ms",
                                        query, totalHits, results.size(), searchTimeMs);

                        if (logger.isDebugEnabled()) {
                                logger.debug("Search results details - query: '{}', hits: {}, took: {}ms",
                                                query, totalHits, response.took());
                        }

                        SearchResponse result = new SearchResponse(totalHits, results);
                        searchCache.put(cacheKey, result);
                        return result;

                } catch (IOException e) {
                        long searchTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                        logger.error("Elasticsearch search failed - query: '{}', userId: {}, searchTime: {}ms, error: {}",
                                        query, userId, searchTimeMs, e.getMessage(), e);
                        throw e;
                } catch (Exception e) {
                        long searchTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                        logger.error("Unexpected error during search - query: '{}', userId: {}, searchTime: {}ms, error: {}",
                                        query, userId, searchTimeMs, e.getMessage(), e);
                        throw new RuntimeException("Search operation failed", e);
                }
        }

        private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
                if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                        throw new IllegalArgumentException("fromDate must be on or before toDate");
                }
        }

        private List<String> stringList(Object value) {
                if (!(value instanceof List<?> values)) {
                        return List.of();
                }

                return values.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .toList();
        }
}
