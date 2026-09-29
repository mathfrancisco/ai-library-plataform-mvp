package com.ailibrary.common.error;
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message){ super(message); }
}
