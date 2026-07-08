package com.smartlend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format for all API errors.
 * Ensures consistent error structure across all endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Only include non-null fields in JSON
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String errorCode;
    private Map<String, String> errors; // Field-level validation errors
    private String path;

    /**
     * Convenience method to create a validation error response.
     */
    public static ErrorResponse validationError(String path, Map<String, String> errors) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(400)
                .error("Validation Failed")
                .message("Input validation failed")
                .errorCode("VALIDATION_ERROR")
                .errors(errors)
                .path(path)
                .build();
    }
}
