package com.ailibrary.auth.service;

import com.ailibrary.auth.repository.RefreshTokenRepository;
import com.ailibrary.auth.repository.UserRepository;
import com.ailibrary.common.security.AuthProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthMaintenance {
    private static final Logger log = LoggerFactory.getLogger(AuthMaintenance.class);
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;

    public AuthMaintenance(UserRepository users, RefreshTokenRepository tokens, AuthProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.properties = properties;
    }

    /** Accounts listed in APP_ADMIN_EMAILS get the ADMIN role, including ones registered earlier. */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void promoteConfiguredAdmins() {
        if (properties.adminEmails().isEmpty()) return;
        int promoted = users.promoteToAdmin(properties.adminEmails());
        if (promoted > 0) log.info("Promoted {} configured account(s) to ADMIN", promoted);
    }

    /** Expired or revoked refresh tokens have no further use after a one-day grace period (SPEC-04 §2.5). */
    @Transactional
    @Scheduled(cron = "${app.auth.token-cleanup-cron:0 17 3 * * *}")
    public void purgeStaleRefreshTokens() {
        int removed = tokens.deleteStale(Instant.now().minus(1, ChronoUnit.DAYS));
        if (removed > 0) log.info("Removed {} stale refresh token(s)", removed);
    }
}
