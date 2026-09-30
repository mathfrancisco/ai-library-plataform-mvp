package com.ailibrary.common.error;

/** AI availability errors: disabled (503), provider timeout (504), provider rate limit (429) or failure (502). */
public class AiException extends ApiException {
    private AiException(ErrorCode code, String message) {
        super(code, message);
    }

    public static AiException disabled() {
        return new AiException(
                ErrorCode.AI_DISABLED, "AI features are disabled; set AI_ENABLED=true and configure GROQ_API_KEY");
    }

    public static AiException timeout() {
        return new AiException(ErrorCode.AI_TIMEOUT, "The AI provider did not answer in time");
    }

    public static AiException rateLimited() {
        return new AiException(
                ErrorCode.AI_RATE_LIMITED, "The AI provider is rate limiting requests; try again shortly");
    }

    public static AiException unavailable() {
        return new AiException(ErrorCode.AI_PROVIDER_ERROR, "The AI provider returned an error");
    }
}
