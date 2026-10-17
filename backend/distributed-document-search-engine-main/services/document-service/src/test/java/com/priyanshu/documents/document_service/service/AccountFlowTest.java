package com.priyanshu.documents.document_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.priyanshu.documents.document_service.controller.AuthController;
import com.priyanshu.documents.document_service.configuration.RecoveryConfiguration;
import com.priyanshu.documents.document_service.entity.User;
import com.priyanshu.documents.document_service.repository.*;
import com.priyanshu.documents.document_service.security.*;
import com.priyanshu.documents.document_service.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = AccountFlowTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.mail.host=localhost",
                "docnest.mail.from=docnest@example.test", "docnest.frontend-url=http://localhost:5173",
                "security.jwt.secret=local-test-only-signing-key-with-more-than-32-bytes"})
class AccountFlowTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AuthController.class, AccountService.class, PasswordRecoveryService.class, RecoveryConfiguration.class,
            AuthRateLimiter.class, JwtService.class, JwtAuthenticationFilter.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean LinkedBlockingQueue<SimpleMailMessage> outbox() { return new LinkedBlockingQueue<>(); }
        @Bean JavaMailSender mailSender(LinkedBlockingQueue<SimpleMailMessage> outbox) {
            JavaMailSender sender = mock(JavaMailSender.class);
            doAnswer(invocation -> { outbox.add(new SimpleMailMessage(invocation.getArgument(0))); return null; })
                    .when(sender).send(any(SimpleMailMessage.class));
            return sender;
        }
    }

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AccountService accounts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LinkedBlockingQueue<SimpleMailMessage> outbox;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String PASSWORD = "A-long-test-passphrase-42";

    @Test void registerLoginRememberMeAndLogoutAreReal() throws Exception {
        String email = "member-" + UUID.randomUUID() + "@example.test";
        assertThat(post("register", Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD), null).statusCode()).isEqualTo(201);
        assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(post("register", Map.of("email", email, "password", PASSWORD), null).statusCode()).isEqualTo(409);
        assertThat(post("login", Map.of("email", email, "password", "incorrect-password"), null).statusCode()).isEqualTo(401);
        assertThat(post("login", Map.of("email", email, "password", "界".repeat(25)), null).statusCode()).isEqualTo(401);
        String shortToken = login(email, false);
        String rememberedToken = login(email, true);
        assertThat(accounts.requireSession(shortToken).getExpiresAt()).isBetween(Instant.now().plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));
        assertThat(accounts.requireSession(rememberedToken).getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(sessions.existsById(shortToken)).isFalse();
        assertThat(sessions.existsById(AccountSecrets.hash(shortToken))).isTrue();
        assertThat(get("session", shortToken).statusCode()).isEqualTo(200);
        assertThat(post("logout", null, shortToken).statusCode()).isEqualTo(204);
        assertThat(get("session", shortToken).statusCode()).isEqualTo(401);
        assertThat(get("session", rememberedToken).statusCode()).isEqualTo(200);
    }

    @Test void emailResetIsSingleUseAndRevokesOldSessions() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.test";
        accounts.register(email, PASSWORD);
        String oldToken = login(email, true);
        assertThat(get("capabilities", null).body()).contains("true");
        HttpResponse<String> response = post("forgot-password", Map.of("email", email), null);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).doesNotContain("token", "reset-password#");
        SimpleMailMessage message = outbox.poll(10, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        String rawToken = message.getText().split("#token=")[1].split("\\s")[0];
        assertThat(rawToken).hasSize(43);
        User stored = users.findByEmail(email).orElseThrow();
        for (int attempt = 0; attempt < 100 && stored.getResetTokenHash() == null; attempt++) {
            Thread.sleep(20);
            stored = users.findByEmail(email).orElseThrow();
        }
        assertThat(stored.getResetTokenHash()).isEqualTo(AccountSecrets.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(stored.getResetExpiresAt()).isAfter(Instant.now().plusSeconds(14 * 60));
        String next = "New-long-test-passphrase-43";
        assertThat(post("reset-password", Map.of("token", rawToken, "password", next), null).statusCode()).isEqualTo(204);
        assertThat(post("reset-password", Map.of("token", rawToken, "password", next), null).statusCode()).isEqualTo(400);
        assertThat(get("session", oldToken).statusCode()).isEqualTo(401);
        assertThat(post("login", Map.of("email", email, "password", PASSWORD), null).statusCode()).isEqualTo(401);
        assertThat(post("login", Map.of("email", email, "password", next), null).statusCode()).isEqualTo(200);
        assertThat(users.findByEmail(email).orElseThrow().getId()).isEqualTo(stored.getId());
    }

    @Test void expiredResetCannotChangePasswordAndUnknownEmailHasSameResponse() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.test";
        accounts.register(email, PASSWORD);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User user = users.findByEmailForUpdate(email).orElseThrow();
            user.setPasswordReset(AccountSecrets.hash("expired-token"), Instant.now().minusSeconds(1));
            users.save(user);
        });
        assertThat(post("reset-password", Map.of("token", "expired-token", "password", PASSWORD), null).statusCode()).isEqualTo(400);
        var unknown = post("forgot-password", Map.of("email", "unknown-" + UUID.randomUUID() + "@example.test"), null);
        assertThat(unknown.statusCode()).isEqualTo(202);
        assertThat(unknown.body()).contains("If an account exists");
    }

    @Test void changeRequiresCurrentPasswordAndPreservesAccount() throws Exception {
        String email = "change-" + UUID.randomUUID() + "@example.test";
        accounts.register(email, PASSWORD);
        String token = login(email, false);
        assertThat(post("change-password", Map.of("currentPassword", "wrong-password", "newPassword", PASSWORD), token).statusCode()).isEqualTo(400);
        assertThat(get("session", token).statusCode()).isEqualTo(200);
        assertThat(post("change-password", Map.of("currentPassword", PASSWORD, "newPassword", "Another-long-passphrase-44"), token).statusCode()).isEqualTo(204);
        assertThat(get("session", token).statusCode()).isEqualTo(401);
        assertThat(post("login", Map.of("email", email, "password", "Another-long-passphrase-44"), null).statusCode()).isEqualTo(200);
    }

    @Test void rejectsShortAndOverlongUnicodePasswordsAndAnonymousSessions() throws Exception {
        String email = "policy-" + UUID.randomUUID() + "@example.test";
        assertThat(post("register", Map.of("email", email, "password", "short"), null).statusCode()).isEqualTo(400);
        assertThat(post("register", Map.of("email", email, "password", "界".repeat(25)), null).statusCode()).isEqualTo(400);
        assertThat(get("session", null).statusCode()).isEqualTo(401);
        AuthRateLimiter limiter = new AuthRateLimiter();
        limiter.check("test", 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("test", 1, Duration.ofMinutes(1))).hasMessageContaining("429");
    }

    @Test void concurrentResetRequestsCannotUseTheSameLinkTwice() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.test";
        accounts.register(email, PASSWORD);
        String raw = AccountSecrets.randomToken();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User user = users.findByEmailForUpdate(email).orElseThrow();
            user.setPasswordReset(AccountSecrets.hash(raw), Instant.now().plusSeconds(900));
            users.save(user);
        });
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try { accounts.reset(raw, "Concurrent-reset-passphrase-45"); return true; }
                catch (org.springframework.web.server.ResponseStatusException exception) {
                    assertThat(exception.getStatusCode().value()).isEqualTo(400);
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
    }

    private String login(String email, boolean rememberMe) throws Exception {
        var response = post("login", Map.of("email", email, "password", PASSWORD, "rememberMe", rememberMe), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("token").asText();
    }
    private HttpResponse<String> get(String path, String token) throws Exception { return send(path, null, token, false); }
    private HttpResponse<String> post(String path, Object body, String token) throws Exception { return send(path, body, token, true); }
    private HttpResponse<String> send(String path, Object body, String token, boolean post) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/" + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (post) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : mapper.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
