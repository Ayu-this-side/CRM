package com.crm.exception;

/**
 * Thrown when a requested entity cannot be found in the database.
 * Handled by {@link com.crm.exception.GlobalExceptionHandler}
 * to render a friendly 404-style error page.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
