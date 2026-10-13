package com.priyanshu.api_gateway.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when JWT token is invalid
 */
public class InvalidTokenException extends GatewayException {
    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED.value(), "INVALID_TOKEN");
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED.value(), "INVALID_TOKEN");
    }
}
