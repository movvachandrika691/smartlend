package com.smartlend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user is not authenticated or lacks permission.
 */
public class UnauthorizedException extends SmartLendException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    private UnauthorizedException(String message, HttpStatus status) {
        super(message, status, status == HttpStatus.FORBIDDEN
                ? "FORBIDDEN"
                : "UNAUTHORIZED");
    }

    public static UnauthorizedException forbidden(String message) {
        return new UnauthorizedException(message, HttpStatus.FORBIDDEN);
    }
}