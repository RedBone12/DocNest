package com.priyanshu.documents.document_service.controller;

import java.time.Duration;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import com.priyanshu.documents.document_service.service.AccountService;
import com.priyanshu.documents.document_service.service.PasswordRecoveryService;
import com.priyanshu.documents.document_service.security.AuthRateLimiter;
import com.priyanshu.documents.document_service.dto.RecoveryRequests;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.priyanshu.documents.document_service.dto.LoginRequest;
import com.priyanshu.documents.document_service.dto.RegisterRequest;
import com.priyanshu.documents.document_service.dto.TokenResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AccountService accounts;
    private final PasswordRecoveryService recovery;
    private final AuthRateLimiter limits;

    public AuthController(AccountService accounts, PasswordRecoveryService recovery, AuthRateLimiter limits) {
        this.accounts = accounts;
        this.recovery = recovery;
        this.limits = limits;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limit(http, "register", 10);
        accounts.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        limit(http, "login", 30);
        limits.check("login-email:" + AccountService.normalizeEmail(request.email()), 10, Duration.ofMinutes(15));
        return new TokenResponse(accounts.login(request.email(), request.password(), Boolean.TRUE.equals(request.rememberMe())));
    }

    @GetMapping("/capabilities")
    public Map<String, Boolean> capabilities() { return Map.of("passwordResetEnabled", recovery.isAvailable()); }

    @GetMapping("/session")
    public Map<String, String> session(HttpServletRequest http) {
        return Map.of("userId", accounts.requireSession(token(http)).getUserId().toString());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) { accounts.logout(token(http)); }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody RecoveryRequests.Forgot request, HttpServletRequest http) {
        limit(http, "forgot", 10);
        if (!recovery.isAvailable()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Password recovery is not configured. Please contact the administrator.");
        String email = AccountService.normalizeEmail(request.email());
        limits.check("forgot-email:" + email, 3, Duration.ofMinutes(15));
        try {
            recovery.deliver(email);
        } catch (org.springframework.core.task.TaskRejectedException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is busy. Please try again later.");
        }
        return Map.of("message", "If an account exists for this email, a reset link will be sent. Check your inbox and spam folder.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody RecoveryRequests.Reset request, HttpServletRequest http) {
        limit(http, "reset", 20);
        accounts.reset(request.token(), request.password());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@Valid @RequestBody RecoveryRequests.Change request, HttpServletRequest http) {
        limit(http, "change", 10);
        accounts.change(token(http), request.currentPassword(), request.newPassword());
    }

    private void limit(HttpServletRequest http, String action, int count) {
        limits.check(action + ":" + http.getRemoteAddr(), count, Duration.ofMinutes(15));
    }
    private String token(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
