package com.priyanshu.documents.document_service.service;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import com.priyanshu.documents.document_service.entity.*;
import com.priyanshu.documents.document_service.repository.*;
import com.priyanshu.documents.document_service.security.*;

@Service
public class AccountService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyPasswordHash;

    public AccountService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyPasswordHash = passwords.encode(AccountSecrets.randomToken());
    }

    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    @Transactional
    public void register(String email, String password) {
        AccountSecrets.validatePassword(password);
        email = normalizeEmail(email);
        if (email.length() > 320) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is too long.");
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered.");
        try {
            users.saveAndFlush(new User(email, passwords.encode(password)));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered.");
        }
    }

    @Transactional
    public String login(String email, String password, boolean rememberMe) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        // Serialize login with password changes so an old password cannot race a reset.
        User user = users.findByEmailForUpdate(normalizeEmail(email)).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        sessions.deleteByExpiresAtBefore(Instant.now());
        String token = jwt.generateToken(user.getId().toString(), rememberMe);
        sessions.save(new AuthSession(AccountSecrets.hash(token), user.getId(),
                Instant.now().plus(Duration.ofDays(rememberMe ? 30 : 1))));
        return token;
    }

    @Transactional(readOnly = true)
    public AuthSession requireSession(String token) {
        if (token == null || !jwt.isTokenValid(token) || !"user".equals(jwt.extractTokenType(token))) throw unauthorized();
        AuthSession session = sessions.findById(AccountSecrets.hash(token)).orElseThrow(AccountService::unauthorized);
        if (!session.getExpiresAt().isAfter(Instant.now())
                || !session.getUserId().toString().equals(jwt.extractUserId(token))) throw unauthorized();
        return session;
    }

    @Transactional
    public void logout(String token) {
        if (token != null) sessions.deleteById(AccountSecrets.hash(token));
    }

    @Transactional
    public void reset(String rawToken, String password) {
        AccountSecrets.validatePassword(password);
        User user = users.findByResetTokenHashForUpdate(AccountSecrets.hash(rawToken))
                .orElseThrow(AccountService::invalidReset);
        if (user.getResetExpiresAt() == null || !user.getResetExpiresAt().isAfter(Instant.now())) throw invalidReset();
        replacePassword(user, password);
    }

    @Transactional
    public void change(String token, String currentPassword, String newPassword) {
        AuthSession session = requireSession(token);
        User user = users.findByIdForUpdate(session.getUserId()).orElseThrow(AccountService::unauthorized);
        // Recheck after acquiring the user lock in case another password reset just completed.
        if (!sessions.existsById(AccountSecrets.hash(token))) throw unauthorized();
        if (!passwords.matches(currentPassword, user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        AccountSecrets.validatePassword(newPassword);
        replacePassword(user, newPassword);
    }

    private void replacePassword(User user, String password) {
        user.setPasswordHash(passwords.encode(password));
        user.setPasswordReset(null, null);
        users.save(user);
        sessions.deleteByUserId(user.getId());
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.");
    }
    private static ResponseStatusException invalidReset() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or expired. Request a new link.");
    }
}
