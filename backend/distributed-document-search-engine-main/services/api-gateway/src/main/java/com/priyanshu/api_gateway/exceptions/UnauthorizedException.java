package com.priyanshu.api_gateway.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when authentication/authorization fails
 */
public class UnauthorizedException extends GatewayException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED");
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED");
    }
}
