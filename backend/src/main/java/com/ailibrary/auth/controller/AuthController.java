package com.ailibrary.auth.controller;

import com.ailibrary.auth.dto.AuthDtos.*;
import com.ailibrary.auth.service.AccountDeletionService;
import com.ailibrary.auth.service.AuthService;
import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.common.web.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AccountDeletionService accounts;
    private final CurrentUser currentUser;
    private final RequestRateLimiter limiter;

    public AuthController(
            AuthService authService,
            AccountDeletionService accounts,
            CurrentUser currentUser,
            RequestRateLimiter limiter) {
        this.authService = authService;
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limiter.checkAuth(http, "register", request.email());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        limiter.checkAuth(http, "login", request.email());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    /** Signs out every device, even when the client no longer has its refresh token. */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll() {
        authService.logoutAll(currentUser.id());
    }

    @GetMapping("/me")
    public UserView me() {
        return authService.getUser(currentUser.id());
    }

    @PatchMapping("/me")
    public UserView updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(currentUser.id(), request);
    }

    @PostMapping("/me/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(currentUser.id(), request);
    }

    /** Permanently deletes the account and all its data; the password is required as confirmation. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        authService.verifyPassword(currentUser.id(), request.password());
        accounts.delete(currentUser.id());
    }
}
