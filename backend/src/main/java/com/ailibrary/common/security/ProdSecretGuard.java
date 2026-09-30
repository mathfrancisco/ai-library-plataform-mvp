package com.ailibrary.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Refuses to start in the prod profile with the JWT secret shipped in the repository. */
@Component
@Profile("prod")
public class ProdSecretGuard {
    private final AuthProperties properties;

    public ProdSecretGuard(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        if (AuthProperties.DEFAULT_SECRET.equals(properties.jwtSecret())) {
            throw new IllegalStateException("JWT_SECRET must be set in production (openssl rand -base64 48)");
        }
    }
}
