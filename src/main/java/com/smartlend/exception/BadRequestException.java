package com.smartlend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown for invalid client requests.
 * Returns HTTP 400 BAD REQUEST status.
 */
public class BadRequestException extends SmartLendException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_REQUEST, cause);
    }
}
