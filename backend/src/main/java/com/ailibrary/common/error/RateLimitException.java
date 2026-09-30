package com.ailibrary.common.error;

public class RateLimitException extends ApiException {
    public RateLimitException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }
}
