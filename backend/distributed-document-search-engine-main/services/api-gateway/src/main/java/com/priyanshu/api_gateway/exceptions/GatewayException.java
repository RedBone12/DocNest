package com.priyanshu.api_gateway.exceptions;

/**
 * Base exception for API Gateway errors
 */
public class GatewayException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;

    public GatewayException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public GatewayException(String message, Throwable cause, int statusCode, String errorCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
