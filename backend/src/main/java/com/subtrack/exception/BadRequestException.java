package com.subtrack.exception;

import org.springframework.http.HttpStatus;

/** Request breaks a business rule (400). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
