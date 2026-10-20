package com.priyanshu.documents.document_service.dto;

import java.util.List;

public record AiMetadataRequest(
        String summary,
        List<String> tags,
        String status,
        String message
) {
    public AiMetadataRequest {
        tags = tags == null ? null : List.copyOf(tags);
    }
}
