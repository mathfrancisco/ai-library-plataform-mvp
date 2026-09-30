package com.ailibrary.common.error;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        this(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode code, String message) {
        super(code, message);
    }
}
