package com.priyanshu.api_gateway.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when downstream service request times out
 */
public class GatewayTimeoutException extends GatewayException {
    public GatewayTimeoutException(String message) {
        super(message, HttpStatus.GATEWAY_TIMEOUT.value(), "GATEWAY_TIMEOUT");
    }

    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, cause, HttpStatus.GATEWAY_TIMEOUT.value(), "GATEWAY_TIMEOUT");
    }
}
