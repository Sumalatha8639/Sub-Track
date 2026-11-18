package com.subtrack.exception;

import org.springframework.http.HttpStatus;

/** Bad credentials or invalid token (401). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
