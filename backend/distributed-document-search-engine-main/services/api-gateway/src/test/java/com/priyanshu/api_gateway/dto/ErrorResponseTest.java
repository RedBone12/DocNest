package com.priyanshu.api_gateway.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorResponseTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void defaultConstructorInitializesTimestamp() {
        LocalDateTime before = LocalDateTime.now();
        ErrorResponse response = new ErrorResponse();
        assertNotNull(response.getTimestamp());
        assertFalse(response.getTimestamp().isBefore(before));
        assertFalse(response.getTimestamp().isAfter(LocalDateTime.now()));
    }

    @Test
    void jsonRoundTripPreservesApiFieldsAndSnakeCaseErrorCode() throws Exception {
        String json = """
                {"timestamp":"2026-10-20T12:30:00","status":401,
                 "error_code":"INVALID_TOKEN","message":"Token expired",
                 "path":"/api/documents"}
                """;
        ErrorResponse response = mapper.readValue(json, ErrorResponse.class);
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 30), response.getTimestamp());
        assertEquals(401, response.getStatus());
        assertEquals("INVALID_TOKEN", response.getErrorCode());
        assertEquals("Token expired", response.getMessage());
        assertEquals("/api/documents", response.getPath());

        JsonNode output = mapper.readTree(mapper.writeValueAsString(response));
        assertEquals("INVALID_TOKEN", output.get("error_code").asText());
        assertFalse(output.has("errorCode"));
        assertEquals(401, output.get("status").asInt());
        assertEquals("Token expired", output.get("message").asText());
        assertEquals("/api/documents", output.get("path").asText());
        assertEquals(response.getTimestamp(), mapper.treeToValue(output, ErrorResponse.class).getTimestamp());
    }
}
