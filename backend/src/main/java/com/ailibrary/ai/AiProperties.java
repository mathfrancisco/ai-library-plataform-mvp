package com.ailibrary.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled, String provider, Models models, Integer rateLimitPerMinute, Integer globalRatePerMinute) {

    public record Models(String fast, String smart) {}

    public AiProperties {
        if (provider == null || provider.isBlank()) provider = "groq";
        if (models == null) models = new Models(null, null);
        models = new Models(
                models.fast() == null || models.fast().isBlank() ? "openai/gpt-oss-20b" : models.fast(),
                models.smart() == null || models.smart().isBlank() ? "openai/gpt-oss-120b" : models.smart());
        if (rateLimitPerMinute == null || rateLimitPerMinute < 1) rateLimitPerMinute = 20;
        if (globalRatePerMinute == null || globalRatePerMinute < 1) globalRatePerMinute = 25;
    }

    public String model(ModelTier tier) {
        return tier == ModelTier.FAST ? models.fast() : models.smart();
    }
}
