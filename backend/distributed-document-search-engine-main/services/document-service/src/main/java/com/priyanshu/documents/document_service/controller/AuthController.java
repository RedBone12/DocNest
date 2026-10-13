package com.priyanshu.documents.document_service.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.priyanshu.documents.document_service.dto.LoginRequest;
import com.priyanshu.documents.document_service.dto.RegisterRequest;
import com.priyanshu.documents.document_service.dto.TokenResponse;
import com.priyanshu.documents.document_service.entity.User;
import com.priyanshu.documents.document_service.repository.UserRepository;
import com.priyanshu.documents.document_service.security.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwtService) {
        this.users = users;
        this.passwords = passwords;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        users.save(new User(email, passwords.encode(request.password())));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email).orElseThrow(this::invalidCredentials);
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return new TokenResponse(jwtService.generateToken(user.getId().toString()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
