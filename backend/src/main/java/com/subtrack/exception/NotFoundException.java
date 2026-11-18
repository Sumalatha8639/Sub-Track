package com.subtrack.exception;

import org.springframework.http.HttpStatus;

/** Resource missing, or owned by another user (404). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
