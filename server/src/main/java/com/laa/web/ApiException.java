package com.laa.web;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * An error with a status and a message that is safe to show the client, plus an optional {@code code} the app can
 * act on (for example, to show a field it hides until it's needed).
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final List<String> errors;
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public ApiException(HttpStatus status, String message, List<String> errors) {
        this(status, message, errors, null);
    }

    private ApiException(HttpStatus status, String message, List<String> errors, String code) {
        super(message);
        this.status = status;
        this.errors = List.copyOf(errors);
        this.code = code;
    }

    public static ApiException withCode(HttpStatus status, String code, String message) {
        return new ApiException(status, message, List.of(), code);
    }

    public HttpStatus status() {
        return status;
    }

    public List<String> errors() {
        return errors;
    }

    /** Null unless the app is meant to act on this error. */
    public String code() {
        return code;
    }
}
