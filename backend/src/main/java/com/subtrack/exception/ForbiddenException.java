package com.subtrack.exception;

import org.springframework.http.HttpStatus;

/** Request is understood but not allowed, e.g. wrong Origin on a cookie endpoint (403). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
