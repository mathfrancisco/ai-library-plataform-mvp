package com.ailibrary.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String jwtSecret, long accessTtlMinutes, long refreshTtlDays) {}
