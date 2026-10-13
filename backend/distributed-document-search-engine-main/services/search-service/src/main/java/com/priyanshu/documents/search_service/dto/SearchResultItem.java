package com.priyanshu.documents.search_service.dto;

import java.util.List;

public record SearchResultItem(
    String documentId,
    String title,
    String description,
    Long fileSize,
    String status,
    String createdAt,
    List<String> contentHighlights,
    String aiSummary,
    List<String> aiTags,
    String aiStatus,
    String aiMessage
) {

}
