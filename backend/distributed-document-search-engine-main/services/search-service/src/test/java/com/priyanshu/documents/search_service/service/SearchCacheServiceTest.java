package com.priyanshu.documents.search_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.dto.SearchResultItem;

class SearchCacheServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ObjectMapper mapper;
    private SearchCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        mapper = new ObjectMapper();
        service = new SearchCacheService(redis, mapper);
        when(redis.opsForValue()).thenReturn(values);
    }

    private String defaultKey() {
        return service.buildKey("owner", "report", List.of("finance"), null, null, 0, 10);
    }

    private SearchResponse sampleResponse() {
        return new SearchResponse(21, List.of(new SearchResultItem(
                "doc-1", "Annual Report", "Financial results", 1024L, "READY",
                "2026-01-20T12:00:00Z", List.of("A [[HIGHLIGHT]]report[[/HIGHLIGHT]]"),
                "Summary", List.of("finance", "tax"), "READY", null)));
    }

    @Test
    void equivalentQueriesAndTagsShareTheSameKey() {
        String first = service.buildKey("owner", "  Annual REPORT  ",
                List.of(" Tax ", "FINANCE", "tax", "", "  "), null, null, 0, 10);
        String second = service.buildKey("owner", "annual report",
                List.of("finance", "tax"), null, null, 0, 10);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void emptyAndWhitespaceOnlyFiltersShareTheSameKey() {
        assertThat(service.buildKey("owner", "", List.of(), null, null, 0, 10))
                .isEqualTo(service.buildKey("owner", "   ", List.of(" ", ""), null, null, 0, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void differentSearchInputsCannotReuseTheSameCacheEntry(int changedInput) {
        String baseline = defaultKey();
        String key = service.buildKey(
                changedInput == 0 ? "other-owner" : "owner",
                changedInput == 1 ? "invoice" : "report",
                changedInput == 2 ? List.of("tax") : List.of("finance"),
                changedInput == 3 ? LocalDate.of(2026, 1, 1) : null,
                changedInput == 4 ? LocalDate.of(2026, 2, 1) : null,
                changedInput == 5 ? 1 : 0,
                changedInput == 6 ? 20 : 10);
        assertThat(key).isNotEqualTo(baseline);
    }

    @Test
    void ownerIdsRemainCaseSensitive() {
        assertThat(defaultKey()).isNotEqualTo(
                service.buildKey("OWNER", "report", List.of("finance"), null, null, 0, 10));
    }

    @Test
    void unicodeAndDelimiterCharactersAreEncodedWithoutKeyCollisions() {
        String first = service.buildKey("用户:a", "Résumé:报告", List.of("财务"), null, null, 0, 10);
        String second = service.buildKey("用户", "a:Résumé:报告", List.of("财务"), null, null, 0, 10);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("用户", "Résumé", "报告", "财务");
        assertThat(first.split(":", -1)).hasSize(11);
    }

    @Test
    void versionChangeInvalidatesPreviouslyGeneratedKeys() {
        when(values.get("docnest:search:version")).thenReturn("7", "8");
        String oldKey = defaultKey();
        String newKey = defaultKey();
        assertThat(oldKey).startsWith("docnest:search:v2:7:");
        assertThat(newKey).startsWith("docnest:search:v2:8:").isNotEqualTo(oldKey);
    }

    @Test
    void missingVersionDefaultsToZero() {
        assertThat(defaultKey()).startsWith("docnest:search:v2:0:");
    }

    @Test
    void versionReadFailureFallsBackToZero() {
        when(values.get("docnest:search:version"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        assertThat(defaultKey()).startsWith("docnest:search:v2:0:");
    }

    @Test
    void cacheMissReturnsNull() {
        assertThat(service.get("missing-key")).isNull();
        verify(values).get("missing-key");
        verifyNoMoreInteractions(values);
    }

    @Test
    void cacheHitDeserializesAllResponseFields() throws Exception {
        SearchResponse expected = sampleResponse();
        when(values.get("key")).thenReturn(mapper.writeValueAsString(expected));
        assertThat(service.get("key")).isEqualTo(expected);
    }

    @Test
    void emptySearchResponseIsAValidCacheHit() {
        when(values.get("key")).thenReturn("{\"total\":0,\"results\":[]}");
        assertThat(service.get("key")).isEqualTo(new SearchResponse(0, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{", "", "{\"total\":\"invalid\",\"results\":[]}"})
    void corruptedCacheDataFallsBackToACacheMiss(String json) {
        when(values.get("key")).thenReturn(json);
        assertThat(service.get("key")).isNull();
    }

    @Test
    void redisReadFailureFallsBackToACacheMiss() {
        when(values.get("key")).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        assertThat(service.get("key")).isNull();
    }

    @Test
    void unavailableValueOperationsDoNotPreventFallback() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        assertThat(defaultKey()).startsWith("docnest:search:v2:0:");
        assertThat(service.get("key")).isNull();
        assertThatCode(() -> service.put("key", sampleResponse())).doesNotThrowAnyException();
    }

    @Test
    void putSerializesTheCompleteResponseWithThirtySecondTtl() throws Exception {
        SearchResponse expected = sampleResponse();
        service.put("key", expected);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("key"), json.capture(), eq(Duration.ofSeconds(30)));
        assertThat(mapper.readValue(json.getValue(), SearchResponse.class)).isEqualTo(expected);
        verifyNoMoreInteractions(values);
    }

    @Test
    void cachedResponseCanBeReadBackWithoutLosingMetadata() {
        // Capture the actual serialized payload and return it through the read path.
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        SearchResponse expected = sampleResponse();
        service.put("key", expected);
        verify(values).set(eq("key"), json.capture(), eq(Duration.ofSeconds(30)));
        when(values.get("key")).thenReturn(json.getValue());
        assertThat(service.get("key")).isEqualTo(expected);
    }

    @Test
    void redisWriteFailureDoesNotDiscardTheSearchResult() {
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(values).set(eq("key"), anyString(), eq(Duration.ofSeconds(30)));
        assertThatCode(() -> service.put("key", sampleResponse())).doesNotThrowAnyException();
        verify(values).set(eq("key"), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test
    void serializationFailureDoesNotAttemptARedisWrite() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        SearchResponse response = sampleResponse();
        when(failingMapper.writeValueAsString(response))
                .thenThrow(new JsonProcessingException("Cannot serialize") {});
        SearchCacheService cache = new SearchCacheService(redis, failingMapper);
        assertThatCode(() -> cache.put("key", response)).doesNotThrowAnyException();
        verifyNoInteractions(values);
    }
}
