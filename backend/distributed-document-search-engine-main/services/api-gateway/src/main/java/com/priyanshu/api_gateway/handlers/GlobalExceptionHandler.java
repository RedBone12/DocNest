package com.priyanshu.api_gateway.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ServerWebExchange;

import com.priyanshu.api_gateway.dto.ErrorResponse;
import com.priyanshu.api_gateway.exceptions.GatewayException;
import com.priyanshu.api_gateway.exceptions.GatewayTimeoutException;
import com.priyanshu.api_gateway.exceptions.InvalidTokenException;
import com.priyanshu.api_gateway.exceptions.ServiceUnavailableException;
import com.priyanshu.api_gateway.exceptions.UnauthorizedException;

import reactor.core.publisher.Mono;

/**
 * Global exception handler for API Gateway
 * Handles all exceptions and returns standardized error responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle UnauthorizedException
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnauthorizedException(
            UnauthorizedException ex,
            ServerWebExchange exchange) {
        logger.warn("Unauthorized access attempt: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse));
    }

    /**
     * Handle InvalidTokenException
     */
    @ExceptionHandler(InvalidTokenException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidTokenException(
            InvalidTokenException ex,
            ServerWebExchange exchange) {
        logger.warn("Invalid token provided: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse));
    }

    /**
     * Handle ServiceUnavailableException
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServiceUnavailableException(
            ServiceUnavailableException ex,
            ServerWebExchange exchange) {
        logger.error("Downstream service unavailable: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse));
    }

    /**
     * Handle GatewayTimeoutException
     */
    @ExceptionHandler(GatewayTimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGatewayTimeoutException(
            GatewayTimeoutException ex,
            ServerWebExchange exchange) {
        logger.error("Gateway timeout: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorResponse));
    }

    /**
     * Handle custom GatewayException
     */
    @ExceptionHandler(GatewayException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGatewayException(
            GatewayException ex,
            ServerWebExchange exchange) {
        logger.error("Gateway error: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getStatusCode(),
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(ex.getStatusCode())
                .body(errorResponse));
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange) {
        logger.error("Unexpected error occurred: ", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                exchange.getRequest().getPath().value()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse));
    }
}
