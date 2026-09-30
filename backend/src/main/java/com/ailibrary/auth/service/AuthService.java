package com.ailibrary.auth.service;

import com.ailibrary.auth.domain.RefreshToken;
import com.ailibrary.auth.domain.User;
import com.ailibrary.auth.dto.AuthDtos.*;
import com.ailibrary.auth.repository.RefreshTokenRepository;
import com.ailibrary.auth.repository.UserRepository;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.common.security.AuthProperties;
import com.ailibrary.common.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties properties) {
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
            throw emailTaken();
        }
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim());
        if (properties.adminEmails().contains(email)) user.promoteToAdmin();
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException concurrentRegistration) {
            throw emailTaken();
        }
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issuePair(user);
    }

    /**
     * Rotates the refresh token. Presenting an already-revoked token means it was copied and reused, so every
     * session of that user is revoked (reuse detection). Committed even though the request fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken current = refreshTokens
                .findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> invalidRefresh("Invalid refresh token"));
        if (current.getRevokedAt() != null) {
            refreshTokens.revokeAll(current.getUserId(), Instant.now());
            throw invalidRefresh("Refresh token was already used; all sessions were signed out");
        }
        if (!current.isUsable()) {
            throw invalidRefresh("Refresh token expired");
        }
        current.revoke();
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        return issuePair(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokens.findByTokenHash(hash(request.refreshToken())).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserView getUser(UUID id) {
        User user =
                users.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        return toView(user);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokens.revokeAll(userId, Instant.now());
    }

    @Transactional
    public UserView updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = user(userId);
        user.rename(request.displayName().trim());
        return toView(user);
    }

    /** Changing the password signs out every other session and returns a fresh pair for this one. */
    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = user(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD, "Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(userId, Instant.now());
        return issuePair(user);
    }

    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        if (!passwordEncoder.matches(password, user(userId).getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD, "Password is incorrect");
        }
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    private static ApiException emailTaken() {
        return new ApiException(ErrorCode.EMAIL_TAKEN, "Email already registered");
    }

    private AuthResponse issuePair(User user) {
        String rawRefresh = randomToken();
        RefreshToken token = new RefreshToken(
                user.getId(), hash(rawRefresh), Instant.now().plus(properties.refreshTtlDays(), ChronoUnit.DAYS));
        refreshTokens.save(token);
        return new AuthResponse(jwtService.issueAccessToken(user), rawRefresh, toView(user));
    }

    private UserView toView(User user) {
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getCreatedAt());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    private static ApiException invalidRefresh(String message) {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, message);
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
