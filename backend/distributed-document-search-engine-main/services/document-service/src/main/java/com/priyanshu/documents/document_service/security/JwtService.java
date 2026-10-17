package com.priyanshu.documents.document_service.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

@Service
public class JwtService {

    private final Key key;

    public JwtService(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    private Key getKey() {
        return key;
    }

    public String generateToken(String userId) {
        return generateToken(userId, false);
    }

    public String generateToken(String userId, boolean rememberMe) {
        return Jwts.builder()
                .setSubject(userId)
                .setId(java.util.UUID.randomUUID().toString())
                .claim("type", "user")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()
                        + java.time.Duration.ofDays(rememberMe ? 30 : 1).toMillis()))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String extractTokenType(String token) {
    return getClaims(token).get("type", String.class);
}

    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }


}
