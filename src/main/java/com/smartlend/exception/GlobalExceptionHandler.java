package com.smartlend.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that catches all exceptions and returns
 * consistent JSON error responses across all API endpoints.
 *
 * Covers:
 * - Custom SmartLend exceptions (400, 401, 403, 404)
 * - Spring Validation errors (@Valid)
 * - Spring Security exceptions
 * - Malformed / unreadable request bodies
 * - Missing headers and parameters
 * - Wrong HTTP method or media type
 * - Rate limiting
 * - All uncaught exceptions (500)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────
    // Custom SmartLend Exceptions
    // ─────────────────────────────────────────────

    /**
     * Handle all SmartLend custom exceptions (Bad Request, Unauthorized, Not Found, etc.)
     */
    @ExceptionHandler(SmartLendException.class)
    public ResponseEntity<ErrorResponse> handleSmartLendException(
            SmartLendException ex, HttpServletRequest request) {
        log.error("SmartLend exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, ex.getStatus());
    }

    // ─────────────────────────────────────────────
    // Validation Errors
    // ─────────────────────────────────────────────

    /**
     * Handle @Valid bean validation failures.
     * Returns field-level error map so clients know exactly what to fix.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed on request to {}", request.getRequestURI());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ErrorResponse response = ErrorResponse.validationError(
                request.getRequestURI(),
                errors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle unreadable or malformed request bodies.
     * Catches JSON parse errors, wrong types, missing body entirely.
     * This is what was causing "Invalid request content." from Spring's default handler.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
                System.out.println("========== GLOBAL HANDLER ==========");
        log.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Request body is missing or contains invalid JSON. Please check your request format.")
                .errorCode("INVALID_REQUEST_BODY")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle missing required request parameters (e.g. @RequestParam).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Required parameter '" + ex.getParameterName() + "' is missing.")
                .errorCode("MISSING_PARAMETER")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle wrong type for path variable or request param.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        String message = "Invalid value for parameter '" + ex.getName() + "'.";
        if ("status".equals(ex.getName())) {
            message = "Invalid loan status. Allowed values: PENDING, UNDER_REVIEW, APPROVED, REJECTED";
        }
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .errorCode("INVALID_PARAMETER")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // ─────────────────────────────────────────────
    // Security Exceptions
    // ─────────────────────────────────────────────

    /**
     * Handle wrong email/password at login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Failed login attempt on {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Invalid email or password.")
                .errorCode("INVALID_CREDENTIALS")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle disabled accounts.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(
            DisabledException ex, HttpServletRequest request) {
        log.warn("Disabled account login attempt on {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Your account has been disabled. Please contact support.")
                .errorCode("ACCOUNT_DISABLED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle locked accounts.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(
            LockedException ex, HttpServletRequest request) {
        log.warn("Locked account login attempt on {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Your account has been locked. Please contact support.")
                .errorCode("ACCOUNT_LOCKED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle any other Spring Security authentication failures.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication exception on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Authentication failed. Please log in again.")
                .errorCode("AUTHENTICATION_FAILED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle 403 Forbidden — authenticated but not authorized.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied to {} for request {}", request.getRemoteUser(), request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to access this resource.")
                .errorCode("ACCESS_DENIED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // ─────────────────────────────────────────────
    // HTTP Level Errors
    // ─────────────────────────────────────────────

    /**
     * Handle wrong HTTP method (e.g. GET instead of POST).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not allowed: {} on {}", ex.getMethod(), request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.")
                .errorCode("METHOD_NOT_ALLOWED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handle unsupported media type (e.g. sending XML instead of JSON).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Unsupported media type on {}: {}", request.getRequestURI(), ex.getContentType());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Unsupported Media Type")
                .message("Content type is not supported. Please use application/json.")
                .errorCode("UNSUPPORTED_MEDIA_TYPE")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Handle endpoint not found (404).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Endpoint not found: {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message("The requested endpoint does not exist.")
                .errorCode("ENDPOINT_NOT_FOUND")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ─────────────────────────────────────────────
    // Rate Limiting
    // ─────────────────────────────────────────────

    /**
     * Handle rate limit exceeded.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .errorCode("RATE_LIMIT_EXCEEDED")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─────────────────────────────────────────────
    // Fallback
    // ─────────────────────────────────────────────

    /**
     * Catch-all for any unhandled exception.
     * Logs full stack trace but returns safe generic message to client.
     * Never expose internal error details in production.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: ", request.getRequestURI(), ex);
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .errorCode("INTERNAL_ERROR")
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}