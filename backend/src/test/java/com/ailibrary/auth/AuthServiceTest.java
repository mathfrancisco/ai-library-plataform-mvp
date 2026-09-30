package com.ailibrary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ailibrary.auth.domain.RefreshToken;
import com.ailibrary.auth.domain.User;
import com.ailibrary.auth.dto.AuthDtos.*;
import com.ailibrary.auth.repository.RefreshTokenRepository;
import com.ailibrary.auth.repository.UserRepository;
import com.ailibrary.auth.service.AuthService;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.security.AuthProperties;
import com.ailibrary.common.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final AuthProperties props = new AuthProperties("0123456789abcdef0123456789abcdef", 15, 30, null, null);
    private final Map<String, RefreshToken> stored = new HashMap<>();
    private AuthService service;

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setUp() {
        service = new AuthService(users, tokens, NoOpPasswordEncoder.getInstance(), new JwtService(props), props);
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(tokens.save(any())).thenAnswer(i -> {
            RefreshToken t = i.getArgument(0);
            stored.put(t.getTokenHash(), t);
            return t;
        });
        when(tokens.findByTokenHash(anyString()))
                .thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
    }

    @Test
    void refreshTokensAreStoredHashedAndRotated() throws Exception {
        AuthResponse registered = service.register(new RegisterRequest(" Dev@Example.com ", "password1", "Dev"));
        assertThat(registered.user().email()).isEqualTo("dev@example.com");
        assertThat(stored).doesNotContainKey(registered.refreshToken()).containsKey(sha256(registered.refreshToken()));

        User user = new User("dev@example.com", "password1", "Dev");
        when(users.findById(any())).thenReturn(Optional.of(user));
        AuthResponse rotated = service.refresh(new RefreshRequest(registered.refreshToken()));
        assertThat(rotated.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThat(stored.get(sha256(registered.refreshToken())).getRevokedAt()).isNotNull();

        // Reusing a rotated token is treated as theft: every session of the user is revoked.
        assertThatThrownBy(() -> service.refresh(new RefreshRequest(registered.refreshToken())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void logoutRevokesToken() throws Exception {
        AuthResponse registered = service.register(new RegisterRequest("a@b.co", "password1", "A"));
        service.logout(new LogoutRequest(registered.refreshToken()));
        assertThat(stored.get(sha256(registered.refreshToken())).isUsable()).isFalse();
    }

    @Test
    void duplicateEmailAndBadPasswordAreRejected() {
        when(users.existsByEmailIgnoreCase("a@b.co")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.co", "password1", "A")))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("EMAIL_TAKEN"));
        when(users.findByEmailIgnoreCase("x@y.co")).thenReturn(Optional.of(new User("x@y.co", "right", "X")));
        assertThatThrownBy(() -> service.login(new LoginRequest("x@y.co", "wrong")))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_CREDENTIALS"));
        ArgumentCaptor<RefreshToken> none = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens, never()).save(none.capture());
    }

    private static String sha256(String v) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    }
}
