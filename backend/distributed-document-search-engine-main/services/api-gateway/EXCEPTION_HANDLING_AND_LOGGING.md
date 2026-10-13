# API Gateway - Exception Handling and Logging

This document describes the exception handling and logging infrastructure implemented in the API Gateway service.

## Exception Handling

### Exception Hierarchy

The API Gateway implements a custom exception hierarchy for better error management:

```
├── GatewayException (base exception)
│   ├── UnauthorizedException
│   ├── InvalidTokenException
│   ├── ServiceUnavailableException
│   └── GatewayTimeoutException
```

#### Exception Classes

1. **GatewayException** (`com.priyanshu.api_gateway.exceptions.GatewayException`)
   - Base exception class for all API Gateway errors
   - Includes HTTP status code and error code
   - Properties:
     - `statusCode`: HTTP status code
     - `errorCode`: Machine-readable error identifier

2. **UnauthorizedException** (`com.priyanshu.api_gateway.exceptions.UnauthorizedException`)
   - Thrown when authentication/authorization fails
   - HTTP Status: 401 Unauthorized
   - Error Code: `UNAUTHORIZED`

3. **InvalidTokenException** (`com.priyanshu.api_gateway.exceptions.InvalidTokenException`)
   - Thrown when JWT token is invalid or expired
   - HTTP Status: 401 Unauthorized
   - Error Code: `INVALID_TOKEN`

4. **ServiceUnavailableException** (`com.priyanshu.api_gateway.exceptions.ServiceUnavailableException`)
   - Thrown when downstream service is unavailable
   - HTTP Status: 503 Service Unavailable
   - Error Code: `SERVICE_UNAVAILABLE`

5. **GatewayTimeoutException** (`com.priyanshu.api_gateway.exceptions.GatewayTimeoutException`)
   - Thrown when request to downstream service times out
   - HTTP Status: 504 Gateway Timeout
   - Error Code: `GATEWAY_TIMEOUT`

### Global Exception Handler

**Class**: `com.priyanshu.api_gateway.handlers.GlobalExceptionHandler`

The `GlobalExceptionHandler` is a `@RestControllerAdvice` that provides centralized exception handling for the entire application. It catches all exceptions and returns standardized error responses.

#### Features

- Centralized exception handling across all controllers
- Standardized error response format (ErrorResponse DTO)
- Appropriate logging for each exception type
- Reactive-compatible (returns `Mono<ResponseEntity<ErrorResponse>>`)

#### Error Response Format

All errors are returned in the following JSON format:

```json
{
  "timestamp": "2026-01-30T10:15:30.123",
  "status": 401,
  "error_code": "INVALID_TOKEN",
  "message": "JWT token is expired or invalid",
  "path": "/documents/123"
}
```

#### Handled Exceptions

1. **UnauthorizedException** → 401 Unauthorized
2. **InvalidTokenException** → 401 Unauthorized
3. **ServiceUnavailableException** → 503 Service Unavailable
4. **GatewayTimeoutException** → 504 Gateway Timeout
5. **GatewayException** → HTTP status code from exception
6. **Generic Exception** → 500 Internal Server Error

### Usage Examples

```java
// Throw custom exception
throw new UnauthorizedException("User is not authenticated");

// Throw with cause
throw new InvalidTokenException("Token validation failed", originalException);

// Service unavailable
throw new ServiceUnavailableException("Document service is down");

// Gateway timeout
throw new GatewayTimeoutException("Request to search service timed out");
```

## Logging

### Logging Configuration

The API Gateway uses **SLF4J** with **Logback** for logging. Configuration is provided through:

1. **application.yaml** - Spring Boot logging configuration
2. **logback-spring.xml** - Advanced Logback configuration

### Logging Levels

| Component | Level | Purpose |
|-----------|-------|---------|
| Root Logger | INFO | General application logging |
| com.priyanshu.api_gateway | DEBUG | API Gateway component logging |
| GlobalExceptionHandler | DEBUG | Exception handling details |
| org.springframework.security | DEBUG | Security framework logging |
| org.springframework.cloud.gateway | DEBUG | Gateway routing details |
| org.springframework.web | INFO | General web framework logging |

### Log Output

#### Console Output
- **Pattern**: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- **Example**: `10:15:30.123 [http-nio-8080-exec-1] DEBUG com.priyanshu.api_gateway.security.SecurityConfig - CSRF protection disabled`

#### File Output
- **Main Log**: `logs/api-gateway.log`
- **Error Log**: `logs/api-gateway-error.log`
- **Pattern**: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- **Rolling Policy**: Size and time-based rolling (max 10MB per file, 10 files retained)

### Log Files

1. **api-gateway.log** - All application logs
   - Max size: 10MB per file
   - Retention: 10 files
   - Total cap: 100MB

2. **api-gateway-error.log** - ERROR level logs only
   - Max size: 10MB per file
   - Retention: 10 files
   - Total cap: 50MB

### Logging Usage

#### In Application Code

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyService {
    private static final Logger logger = LoggerFactory.getLogger(MyService.class);

    public void processRequest() {
        logger.debug("Processing request with id: {}", requestId);
        
        try {
            // Do something
            logger.info("Request processed successfully");
        } catch (Exception e) {
            logger.error("Error processing request", e);
            throw new GatewayException("Processing failed", e, 500, "PROCESSING_ERROR");
        }
    }
}
```

#### Key Components with Logging

1. **ApiGatewayApplication**
   - Application startup and shutdown

2. **SecurityConfig**
   - Security configuration initialization
   - Authorization rules setup

3. **JwtDecoderConfig**
   - JWT decoder initialization
   - Token validation configuration

4. **GlobalExceptionHandler**
   - Exception handling with appropriate log levels
   - Warnings for auth failures
   - Errors for service failures

## Configuration in application.yaml

```yaml
logging:
  level:
    root: INFO
    com.priyanshu.api_gateway: DEBUG
    org.springframework.security: DEBUG
    org.springframework.cloud.gateway: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/api-gateway.log
    max-size: 10MB
    max-history: 10
```

## Best Practices

### Exception Handling
1. Use specific exception types for different error scenarios
2. Include meaningful error messages with context
3. Always log exceptions at appropriate levels
4. Return structured error responses to clients

### Logging
1. Use appropriate log levels:
   - **DEBUG**: Detailed diagnostic information
   - **INFO**: General informational messages
   - **WARN**: Warning messages for recoverable issues
   - **ERROR**: Error messages for failures
2. Include context in log messages (user IDs, request IDs, etc.)
3. Avoid logging sensitive information (passwords, tokens)
4. Use placeholders `{}` for dynamic values instead of string concatenation

### Example Pattern

```java
// Good logging practices
logger.debug("Processing request from user: {} for resource: {}", userId, resourceId);
logger.warn("Retry attempt {} for service: {}", retryCount, serviceName);
logger.error("Failed to process request", exception);

// Avoid this
logger.debug("Processing request from user: " + userId); // String concatenation
logger.error("Authentication failed for user: " + password); // Don't log sensitive data
```

## Dependencies

The following dependencies are used for exception handling and logging:

- **spring-boot-starter-logging**: SLF4J and Logback
- **spring-boot-starter-webflux**: Reactive support for error handling
- **spring-boot-starter-security**: Security framework

## Troubleshooting

### Issue: Logs not appearing in files
- **Solution**: Ensure `logs/` directory exists and is writable
- Check `application.yaml` for correct log file configuration

### Issue: Too much logging noise
- **Solution**: Adjust log levels in `logback-spring.xml`
- Increase log level for verbose third-party libraries

### Issue: Log files growing too large
- **Solution**: Check rolling policy configuration
- Reduce `max-history` or `total-sizeCap` values if needed

## Monitoring

Use the following endpoints to monitor the application:

- **Health**: `GET /actuator/health`
- **Info**: `GET /actuator/info`

## Future Enhancements

- Structured logging (JSON format) for better parsing
- Distributed tracing with Spring Cloud Sleuth
- Custom metrics for monitoring exception rates
- Alert integration for ERROR level logs
