package com.buscard.backend.service;

import org.springframework.http.HttpStatus;

/** Domain exception mapped directly to an HTTP error response. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
