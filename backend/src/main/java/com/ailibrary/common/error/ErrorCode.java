package com.ailibrary.common.error;

import org.springframework.http.HttpStatus;

/** Stable, client-visible error codes. The frontend switches on these; never rename one casually. */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST),
    EMPTY_FILE(HttpStatus.BAD_REQUEST),
    TOO_LARGE_AFTER_EXTRACTION(HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_READY(HttpStatus.BAD_REQUEST),
    NO_BOOK_DOCUMENTS(HttpStatus.BAD_REQUEST),
    NO_SUMMARY_SOURCE(HttpStatus.BAD_REQUEST),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST),
    QUOTA_EXCEEDED(HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    LIBRARY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    EXTERNAL_BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    CONFLICT(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    BOOK_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ALREADY_IN_LIBRARY(HttpStatus.CONFLICT),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
    AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
