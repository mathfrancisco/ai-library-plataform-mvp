package com.ailibrary.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTtlMinutes,
        long refreshTtlDays,
        List<String> corsAllowedOrigins,
        List<String> adminEmails) {
    public AuthProperties {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty())
            corsAllowedOrigins = List.of("http://localhost:3000");
        adminEmails = adminEmails == null
                ? List.of()
                : adminEmails.stream()
                        .map(String::trim)
                        .filter(e -> !e.isEmpty())
                        .map(String::toLowerCase)
                        .toList();
    }
}
