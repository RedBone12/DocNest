package com.priyanshu.documents.search_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.dto.SearchResultItem;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.transport.ElasticsearchTransport;

class DocumentSearchServiceTest {
    private ElasticsearchClient client;
    private ElasticsearchTransport transport;
    private SearchCacheService cache;
    private DocumentSearchService service;
    private SearchRequest capturedRequest;

    @BeforeEach
    void setUp() {
        transport = mock(ElasticsearchTransport.class);
        client = new ElasticsearchClient(transport);
        cache = mock(SearchCacheService.class);
        service = new DocumentSearchService(client, cache);
        when(cache.buildKey(anyString(), anyString(), anyList(), nullable(LocalDate.class),
                nullable(LocalDate.class), anyInt(), anyInt())).thenReturn("search-key");
    }

    // Execute the real request builder supplied by the service, rather than mocking the query DSL.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void respondWith(long total, List<Hit<Map>> hits) throws IOException {
        var response = new co.elastic.clients.elasticsearch.core.SearchResponse.Builder<Map>()
                .took(1).timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.total(t -> t.value(total).relation(
                        co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq)).hits(hits))
                .build();
        when(transport.performRequest(any(SearchRequest.class), any(), any())).thenAnswer(call -> {
            capturedRequest = call.getArgument(0);
            return response;
        });
    }

    private SearchResponse search(String query) throws IOException {
        return service.search(query, List.of(), null, null, 0, 10, "owner-1");
    }

    @Test
    void cacheHitReturnsCachedResponseWithoutCallingElasticsearch() throws Exception {
        SearchResponse cached = new SearchResponse(0, List.of());
        when(cache.get("search-key")).thenReturn(cached);
        assertThat(search("report")).isSameAs(cached);
        verifyNoInteractions(transport);
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void forwardsAllSearchInputsToTheCacheKey() throws Exception {
        respondWith(0, List.of());
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        service.search("report", List.of("finance"), from, to, 2, 15, "owner-2");
        verify(cache).buildKey("owner-2", "report", List.of("finance"), from, to, 2, 15);
        assertThat(capturedRequest.from()).isEqualTo(30);
        assertThat(capturedRequest.size()).isEqualTo(15);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "report"})
    void everyQueryIncludesAnExactOwnerFilter(String query) throws Exception {
        respondWith(0, List.of());
        search(query);
        var filters = capturedRequest.query().bool().filter();
        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).term().field()).isEqualTo("ownerId");
        assertThat(filters.get(0).term().value().stringValue()).isEqualTo("owner-1");
        if (query.isBlank()) {
            assertThat(capturedRequest.query().bool().must()).isEmpty();
        }
    }

    @Test
    void nonBlankQuerySearchesDocumentAndAiTextWithAndSemantics() throws Exception {
        respondWith(0, List.of());
        search("annual report");
        var must = capturedRequest.query().bool().must();
        assertThat(must).hasSize(1);
        var query = must.get(0).multiMatch();
        assertThat(query.query()).isEqualTo("annual report");
        assertThat(query.fields()).containsExactly("title", "description", "content", "aiSummary");
        assertThat(query.operator()).isEqualTo(Operator.And);
    }

    @Test
    void tagFiltersTrimIgnoreBlanksDeduplicateAndMatchCaseInsensitively() throws Exception {
        respondWith(0, List.of());
        service.search("", List.of(" finance ", "", " ", "finance", "tax"), null, null, 0, 10, "owner-1");
        var filters = capturedRequest.query().bool().filter();
        assertThat(filters).hasSize(3);
        assertThat(filters.subList(1, 3)).allSatisfy(filter -> {
            assertThat(filter.wildcard().field()).isEqualTo("aiTags");
            assertThat(filter.wildcard().caseInsensitive()).isTrue();
        });
        assertThat(filters.subList(1, 3)).extracting(filter -> filter.wildcard().value())
                .containsExactly("finance", "tax");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void dateFiltersSupportOpenRangesAndInclusiveSameDayBounds(int scenario) throws Exception {
        respondWith(0, List.of());
        LocalDate day = LocalDate.of(2026, 1, 20);
        LocalDate from = scenario == 0 || scenario == 3 ? day : null;
        LocalDate to = scenario == 1 || scenario == 3 ? day : null;
        service.search("", List.of(), from, to, 0, 10, "owner-1");
        var filters = capturedRequest.query().bool().filter();
        if (scenario == 2) {
            assertThat(filters).hasSize(1);
        } else {
            assertThat(filters).hasSize(2);
            var range = filters.get(1).range();
            assertThat(range.field()).isEqualTo("createdAt");
            if (from == null) assertThat(range.gte()).isNull();
            else assertThat(range.gte().to(String.class)).isEqualTo("2026-01-20T00:00:00Z");
            if (to == null) assertThat(range.lte()).isNull();
            else assertThat(range.lte().to(String.class)).isEqualTo("2026-01-20T23:59:59.999Z");
        }
    }

    @Test
    void reversedDateRangeIsRejectedBeforeCacheOrSearchAccess() {
        assertThatThrownBy(() -> service.search("report", List.of(), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1), 0, 10, "owner-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transport);
        verify(cache, never()).get(any());
        verify(cache, never()).put(any(), any());
    }

    @Test
    void requestsTheExpectedIndexSortAndHighlightMarkers() throws Exception {
        respondWith(0, List.of());
        search("report");
        assertThat(capturedRequest.index()).containsExactly("documents_index_v4");
        assertThat(capturedRequest.from()).isZero();
        assertThat(capturedRequest.sort()).hasSize(1);
        assertThat(capturedRequest.sort().get(0).field().field()).isEqualTo("createdAt");
        assertThat(capturedRequest.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
        assertThat(capturedRequest.highlight().preTags()).containsExactly("[[HIGHLIGHT]]");
        assertThat(capturedRequest.highlight().postTags()).containsExactly("[[/HIGHLIGHT]]");
        var content = capturedRequest.highlight().fields().get("content");
        assertThat(content.fragmentSize()).isEqualTo(160);
        assertThat(content.numberOfFragments()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void mapsSearchMetadataHighlightsAndTotalThenCachesTheResult() throws Exception {
        Map<String, Object> source = new HashMap<>();
        source.put("title", "Annual Report");
        source.put("description", "Financial results");
        source.put("fileSize", 123);
        source.put("status", "READY");
        source.put("createdAt", "2026-01-20T12:00:00Z");
        source.put("aiSummary", "Summary");
        source.put("aiTags", List.of("finance", "report"));
        source.put("aiStatus", "READY");
        source.put("aiMessage", "Complete");
        List<String> highlights = List.of("A [[HIGHLIGHT]]report[[/HIGHLIGHT]]");
        Hit<Map> hit = new Hit.Builder<Map>().index("documents_index_v4").id("doc-1")
                .source(source).highlight("content", highlights).build();
        respondWith(31, List.of(hit));

        SearchResponse response = search("report");

        assertThat(response.total()).isEqualTo(31);
        assertThat(response.results()).containsExactly(new SearchResultItem("doc-1", "Annual Report",
                "Financial results", 123L, "READY", "2026-01-20T12:00:00Z", highlights,
                "Summary", List.of("finance", "report"), "READY", "Complete"));
        verify(cache).put("search-key", response);
    }

    @Test
    void emptyResultsPreserveZeroTotalAndAreCached() throws Exception {
        respondWith(0, List.of());
        SearchResponse result = search("unknown");
        assertThat(result.total()).isZero();
        assertThat(result.results()).isEmpty();
        verify(cache).put("search-key", result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @SuppressWarnings("rawtypes")
    void toleratesMissingOptionalMetadataAndFiltersNonStringAiTags(int scenario) throws Exception {
        Map<String, Object> source = new HashMap<>();
        List<Object> mixed = new ArrayList<>();
        mixed.add("finance");
        mixed.add(42);
        mixed.add(null);
        mixed.add("tax");
        if (scenario == 1) source.put("aiTags", "invalid");
        if (scenario == 2) source.put("aiTags", mixed);
        source.put("fileSize", "invalid");
        respondWith(1, List.of(new Hit.Builder<Map>().index("documents_index_v4").id("doc-1")
                .source(source).build()));
        var item = search("").results().get(0);
        assertThat(item.fileSize()).isNull();
        assertThat(item.contentHighlights()).isEmpty();
        assertThat(item.aiSummary()).isNull();
        assertThat(item.aiStatus()).isNull();
        assertThat(item.aiTags()).containsExactlyElementsOf(scenario == 2 ? List.of("finance", "tax") : List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ioFailureIsRethrownWithoutCachingAResult() throws Exception {
        IOException failure = new IOException("Elasticsearch unavailable");
        when(transport.performRequest(any(SearchRequest.class), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> search("report")).isSameAs(failure);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unexpectedSearchFailureIsWrappedWithItsOriginalCause() throws Exception {
        IllegalStateException failure = new IllegalStateException("Unexpected response");
        when(transport.performRequest(any(SearchRequest.class), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> search("report"))
                .isInstanceOf(RuntimeException.class).hasMessage("Search operation failed").hasCause(failure);
        verify(cache, never()).put(any(), any());
    }
}
