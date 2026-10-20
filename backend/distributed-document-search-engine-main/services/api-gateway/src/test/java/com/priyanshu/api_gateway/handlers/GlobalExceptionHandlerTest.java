package com.priyanshu.api_gateway.handlers;

import com.priyanshu.api_gateway.dto.ErrorResponse;
import com.priyanshu.api_gateway.exceptions.GatewayException;
import com.priyanshu.api_gateway.exceptions.GatewayTimeoutException;
import com.priyanshu.api_gateway.exceptions.InvalidTokenException;
import com.priyanshu.api_gateway.exceptions.ServiceUnavailableException;
import com.priyanshu.api_gateway.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private final MockServerWebExchange exchange =
            MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/documents").build()
            );

    @Test
    void unauthorizedReturns401() {
        var response = handler.handleUnauthorizedException(
                new UnauthorizedException("Authentication required"),
                exchange
        ).block();

        assertError(response, 401, "UNAUTHORIZED",
                "Authentication required");
    }

    @Test
    void invalidTokenReturns401() {
        var response = handler.handleInvalidTokenException(
                new InvalidTokenException("Token expired"),
                exchange
        ).block();

        assertError(response, 401, "INVALID_TOKEN",
                "Token expired");
    }

    @Test
    void unavailableServiceReturns503() {
        var response = handler.handleServiceUnavailableException(
                new ServiceUnavailableException("Search unavailable"),
                exchange
        ).block();

        assertError(response, 503, "SERVICE_UNAVAILABLE",
                "Search unavailable");
    }

    @Test
    void timeoutReturns504() {
        var response = handler.handleGatewayTimeoutException(
                new GatewayTimeoutException("Search timed out"),
                exchange
        ).block();

        assertError(response, 504, "GATEWAY_TIMEOUT",
                "Search timed out");
    }

    @Test
    void gatewayErrorPreservesCustomStatusAndCode() {
        var response = handler.handleGatewayException(
                new GatewayException(
                        "Request limit exceeded", 429, "RATE_LIMITED"
                ),
                exchange
        ).block();

        assertError(response, 429, "RATE_LIMITED",
                "Request limit exceeded");
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() {
        var response = handler.handleGenericException(
                new RuntimeException("Sensitive database details"),
                exchange
        ).block();

        assertError(response, 500, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    private void assertError(
            ResponseEntity<ErrorResponse> response,
            int expectedStatus,
            String expectedCode,
            String expectedMessage
    ) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.getStatusCode().value());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(expectedStatus, body.getStatus());
        assertEquals(expectedCode, body.getErrorCode());
        assertEquals(expectedMessage, body.getMessage());
        assertEquals("/api/documents", body.getPath());
        assertNotNull(body.getTimestamp());
    }
}
