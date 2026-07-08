package com.smartlend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception class for all SmartLend application exceptions.
 * Provides consistent error structure with HTTP status codes.
 */
@Getter
public class SmartLendException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public SmartLendException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = status.name();
    }

    public SmartLendException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public SmartLendException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = status.name();
    }
}
