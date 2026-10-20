package com.priyanshu.api_gateway.exceptions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GatewayExceptionsTest {
    record ExceptionCase(int status, String code,
                         Function<String, GatewayException> simple,
                         BiFunction<String, Throwable, GatewayException> withCause) {}

    static Stream<ExceptionCase> exceptions() {
        return Stream.of(
                new ExceptionCase(429, "RATE_LIMITED",
                        message -> new GatewayException(message, 429, "RATE_LIMITED"),
                        (message, cause) -> new GatewayException(message, cause, 429, "RATE_LIMITED")),
                new ExceptionCase(401, "UNAUTHORIZED", UnauthorizedException::new, UnauthorizedException::new),
                new ExceptionCase(401, "INVALID_TOKEN", InvalidTokenException::new, InvalidTokenException::new),
                new ExceptionCase(503, "SERVICE_UNAVAILABLE", ServiceUnavailableException::new, ServiceUnavailableException::new),
                new ExceptionCase(504, "GATEWAY_TIMEOUT", GatewayTimeoutException::new, GatewayTimeoutException::new));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void messageConstructorPreservesErrorContract(ExceptionCase sample) {
        GatewayException exception = sample.simple().apply("Request failed");
        assertEquals("Request failed", exception.getMessage());
        assertEquals(sample.status(), exception.getStatusCode());
        assertEquals(sample.code(), exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void causeConstructorPreservesOriginalFailure(ExceptionCase sample) {
        Throwable cause = new IllegalStateException("Downstream failure");
        GatewayException exception = sample.withCause().apply("Request failed", cause);
        assertEquals("Request failed", exception.getMessage());
        assertEquals(sample.status(), exception.getStatusCode());
        assertEquals(sample.code(), exception.getErrorCode());
        assertSame(cause, exception.getCause());
    }
}
