package com.ailibrary.common.error;

import org.springframework.http.HttpStatus;

/** AI availability errors: disabled (503), provider timeout (504) or provider rate limit (429). */
public class AiException extends ApiException {
    private AiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static AiException disabled() {
        return new AiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_DISABLED",
                "AI features are disabled; set AI_ENABLED=true and configure GROQ_API_KEY");
    }

    public static AiException timeout() {
        return new AiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "The AI provider did not answer in time");
    }

    public static AiException rateLimited() {
        return new AiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_RATE_LIMITED",
                "The AI provider is rate limiting requests; try again shortly");
    }

    public static AiException unavailable() {
        return new AiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "The AI provider returned an error");
    }
}
