package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiRequestLog;
import com.ailibrary.common.error.AiException;
import com.ailibrary.common.error.ApiException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Single gateway to the chat model: enabled check, per-user and global rate limits, model routing by tier,
 * request logging with the model actually used, and provider error mapping.
 */
@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiFacade {
    private static final Logger log = LoggerFactory.getLogger(AiFacade.class);

    private final ChatClient chatClient;
    private final AiProperties properties;
    private final AiRequestLogWriter logs;
    private final AiRateLimiter rateLimiter;

    public AiFacade(
            ChatClient.Builder builder, AiProperties properties, AiRequestLogWriter logs, AiRateLimiter rateLimiter) {
        this.chatClient = builder.build();
        this.properties = properties;
        this.logs = logs;
        this.rateLimiter = rateLimiter;
    }

    public String complete(UUID userId, String operation, ModelTier tier, String system, String user) {
        ChatResponse response = call(userId, operation, tier, spec -> spec.system(system)
                .user(user)
                .call()
                .chatResponse());
        return text(response);
    }

    public <T> T structured(UUID userId, String operation, ModelTier tier, String system, String user, Class<T> type) {
        var holder = new Object() {
            T entity;
        };
        call(userId, operation, tier, spec -> {
            var result = spec.system(system).user(user).call().responseEntity(type, s -> s.validateSchema());
            holder.entity = result.entity();
            return result.response();
        });
        return holder.entity;
    }

    public String tools(
            UUID userId,
            String operation,
            ModelTier tier,
            String system,
            List<Message> history,
            String user,
            Object... tools) {
        ChatResponse response = call(userId, operation, tier, spec -> spec.system(system)
                .messages(history)
                .user(user)
                .tools(tools)
                .call()
                .chatResponse());
        return text(response);
    }

    public AiProperties properties() {
        return properties;
    }

    private ChatResponse call(
            UUID userId,
            String operation,
            ModelTier tier,
            Function<ChatClient.ChatClientRequestSpec, ChatResponse> fn) {
        if (!properties.enabled()) throw AiException.disabled();
        rateLimiter.check(userId);
        String model = properties.model(tier);
        long started = System.nanoTime();
        try {
            ChatResponse response = fn.apply(
                    chatClient.prompt().options(OpenAiChatOptions.builder().model(model)));
            if (response == null) throw new IllegalStateException("Empty AI response");
            saveLog(userId, operation, model, started, response, null);
            return response;
        } catch (RuntimeException ex) {
            saveLog(userId, operation, model, started, null, ex);
            throw translate(ex);
        }
    }

    private static String text(ChatResponse response) {
        return response.getResult().getOutput().getText();
    }

    /** Maps provider failures to stable API errors; keeps our own ApiExceptions as they are. */
    static RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ApiException) return ex;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
            if (name.contains("ratelimit") || message.contains("429") || message.contains("rate limit")) {
                return AiException.rateLimited();
            }
            if (t instanceof SocketTimeoutException
                    || t instanceof HttpTimeoutException
                    || t instanceof TimeoutException
                    || name.contains("timeout")) {
                return AiException.timeout();
            }
        }
        log.warn("AI provider call failed: {}", ex.toString());
        return AiException.unavailable();
    }

    private void saveLog(
            UUID userId, String operation, String model, long started, ChatResponse response, RuntimeException error) {
        Integer in = null, out = null;
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                in = usage.getPromptTokens();
                out = usage.getCompletionTokens();
            }
        }
        long latency = (System.nanoTime() - started) / 1_000_000L;
        try {
            logs.write(new AiRequestLog(
                    userId,
                    operation,
                    properties.provider(),
                    model,
                    in,
                    out,
                    latency,
                    error == null,
                    error == null ? null : error.getClass().getSimpleName()));
        } catch (RuntimeException logFailure) {
            log.warn("Could not write AI request log: {}", logFailure.getMessage());
        }
    }
}
