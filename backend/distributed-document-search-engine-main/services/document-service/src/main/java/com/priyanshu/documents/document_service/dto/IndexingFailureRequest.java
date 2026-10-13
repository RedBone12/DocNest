package com.priyanshu.documents.document_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record IndexingFailureRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 1000) String message,
        @Positive int failureCount
) {}
