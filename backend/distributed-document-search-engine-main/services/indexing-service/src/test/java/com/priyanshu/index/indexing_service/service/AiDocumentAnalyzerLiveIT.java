package com.priyanshu.index.indexing_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priyanshu.index.indexing_service.dto.AiAnalysis;

class AiDocumentAnalyzerLiveIT {

    @Test
    void realOpenAiCallProducesGroundedSummaryAndTags() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assertThat(apiKey)
                .as("Set OPENAI_API_KEY in the environment before running the ai-live profile")
                .isNotBlank();

        String document;
        try (var stream = getClass().getResourceAsStream("/ai/acceptance-document.txt")) {
            assertThat(stream).as("dedicated AI acceptance document").isNotNull();
            document = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        AiDocumentAnalyzer analyzer = new AiDocumentAnalyzer(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(),
                apiKey,
                model,
                URI.create("https://api.openai.com/v1/responses"),
                Duration.ofSeconds(60),
                3,
                Duration.ofSeconds(1));

        AiAnalysis analysis = analyzer.analyze("Project Cedar Acceptance Document", document);

        assertThat(analysis.status())
                .withFailMessage("AI live acceptance failed: %s", analysis.message())
                .isEqualTo("COMPLETED");
        assertThat(analysis.message()).isNull();
        assertThat(analysis.summary()).isNotBlank().hasSizeBetween(40, 4_000);
        assertThat(analysis.summary().toLowerCase(Locale.ROOT)).contains("cedar");
        assertThat(analysis.tags()).hasSizeBetween(3, 6).doesNotHaveDuplicates();
        assertThat(analysis.tags()).allSatisfy(tag -> assertThat(tag).isNotBlank());
        assertThat(String.join(" ", analysis.tags()).toLowerCase(Locale.ROOT))
                .containsAnyOf("kafka", "elasticsearch", "telemetry", "reliability");
    }
}
