package com.priyanshu.documents.document_service.dto;

import jakarta.validation.constraints.*;

public final class RecoveryRequests {
    private RecoveryRequests() {}
    public record Forgot(@NotBlank @Email @Size(max = 320) String email) {}
    public record Reset(@NotBlank @Size(max = 128) String token, @NotBlank @Size(min = 12, max = 72) String password) {}
    public record Change(@NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(min = 12, max = 72) String newPassword) {}
}
