package com.priyanshu.index.indexing_service.dto;

import java.util.List;

public record AiAnalysis(
        String summary,
        List<String> tags,
        String status,
        String message
) {
    public static AiAnalysis completed(String summary, List<String> tags) {
        return new AiAnalysis(summary, List.copyOf(tags), "COMPLETED", null);
    }

    public static AiAnalysis disabled(String message) {
        return new AiAnalysis(null, List.of(), "DISABLED", message);
    }

    public static AiAnalysis disabled() {
        return disabled("OPENAI_API_KEY is not configured");
    }

    public static AiAnalysis failed(String message) {
        return new AiAnalysis(null, List.of(), "FAILED", message);
    }

    public static AiAnalysis failed() {
        return failed("OpenAI analysis failed");
    }
}
