package com.smartlend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found in database.
 * Returns HTTP 404 NOT FOUND status.
 */
public class ResourceNotFoundException extends SmartLendException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
              HttpStatus.NOT_FOUND,
              "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
