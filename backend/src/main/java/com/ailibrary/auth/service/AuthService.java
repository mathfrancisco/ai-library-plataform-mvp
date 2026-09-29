package com.ailibrary.auth.service;

import com.ailibrary.auth.domain.RefreshToken;
import com.ailibrary.auth.domain.User;
import com.ailibrary.auth.dto.AuthDtos.*;
import com.ailibrary.auth.repository.RefreshTokenRepository;
import com.ailibrary.auth.repository.UserRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.common.security.AuthProperties;
import com.ailibrary.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Email already registered");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.displayName().trim()));
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }
        return issuePair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (!current.isUsable()) {
            throw new BadRequestException("Refresh token expired or revoked");
        }
        current.revoke();
        User user = users.findById(current.getUserId()).orElseThrow(() -> new NotFoundException("User not found"));
        return issuePair(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokens.findByTokenHash(hash(request.refreshToken())).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserView getUser(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        return toView(user);
    }

    private AuthResponse issuePair(User user) {
        String rawRefresh = randomToken();
        RefreshToken token = new RefreshToken(
                user.getId(),
                hash(rawRefresh),
                Instant.now().plus(properties.refreshTtlDays(), ChronoUnit.DAYS)
        );
        refreshTokens.save(token);
        return new AuthResponse(jwtService.issueAccessToken(user), rawRefresh, toView(user));
    }

    private UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
