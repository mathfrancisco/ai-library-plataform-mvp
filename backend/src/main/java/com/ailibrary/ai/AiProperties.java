package com.ailibrary.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean enabled, String provider, String model, Integer maxRequestsPerMinute) {
    public AiProperties {
        if (maxRequestsPerMinute == null || maxRequestsPerMinute < 1) maxRequestsPerMinute = 30;
    }
}
