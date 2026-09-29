package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiRequestLog;
import com.ailibrary.ai.repository.AiRequestLogRepository;
import com.ailibrary.common.error.BadRequestException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiFacade {
    private final ChatClient chatClient;
    private final AiProperties properties;
    private final AiRequestLogRepository logs;
    private final AiRateLimiter rateLimiter;

    public AiFacade(ChatClient.Builder builder, AiProperties properties, AiRequestLogRepository logs, AiRateLimiter rateLimiter) {
        this.chatClient = builder.build();
        this.properties = properties;
        this.logs = logs;
        this.rateLimiter = rateLimiter;
    }

    public String complete(UUID userId, String operation, String system, String user) {
        ensureEnabled();
        rateLimiter.check(userId);
        long started = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt().system(system).user(user).call().chatResponse();
            if (response == null) throw new IllegalStateException("Empty AI response");
            saveLog(userId, operation, started, response, null);
            return response.getResult().getOutput().getText();
        } catch (RuntimeException ex) {
            saveLog(userId, operation, started, null, ex);
            throw ex;
        }
    }

    public <T> T structured(UUID userId, String operation, String system, String user, Class<T> type) {
        ensureEnabled();
        rateLimiter.check(userId);
        long started = System.nanoTime();
        try {
            var result = chatClient.prompt().system(system).user(user).call()
                    .responseEntity(type, spec -> spec.validateSchema());
            saveLog(userId, operation, started, result.response(), null);
            return result.entity();
        } catch (RuntimeException ex) {
            saveLog(userId, operation, started, null, ex);
            throw ex;
        }
    }

    public String tools(UUID userId, String operation, String system, String user, Object... tools) {
        ensureEnabled();
        rateLimiter.check(userId);
        long started = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt().system(system).user(user).tools(tools).call().chatResponse();
            if (response == null) throw new IllegalStateException("Empty AI response");
            saveLog(userId, operation, started, response, null);
            return response.getResult().getOutput().getText();
        } catch (RuntimeException ex) {
            saveLog(userId, operation, started, null, ex);
            throw ex;
        }
    }

    public AiProperties properties() { return properties; }

    private void ensureEnabled() {
        if (!properties.enabled()) throw new BadRequestException("AI features are disabled");
    }

    private void saveLog(UUID userId, String operation, long started, ChatResponse response, RuntimeException error) {
        Integer in = null, out = null;
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) { in = usage.getPromptTokens(); out = usage.getCompletionTokens(); }
        }
        long latency = (System.nanoTime() - started) / 1_000_000L;
        logs.save(new AiRequestLog(userId, operation, properties.provider(), properties.model(), in, out, latency,
                error == null, error == null ? null : error.getClass().getSimpleName()));
    }
}
