package com.ailibrary.common.error;

import org.springframework.http.HttpStatus;

/** Base type for errors that map to a stable, client-visible {@link ErrorCode}. */
public class ApiException extends RuntimeException {
    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public HttpStatus status() {
        return code.status();
    }

    public String code() {
        return code.name();
    }

    public ErrorCode errorCode() {
        return code;
    }
}
