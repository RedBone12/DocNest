package com.priyanshu.api_gateway.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when downstream service is unavailable
 */
public class ServiceUnavailableException extends GatewayException {
    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICE_UNAVAILABLE");
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICE_UNAVAILABLE");
    }
}
