package com.priyanshu.documents.document_service.service;

import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.priyanshu.documents.document_service.repository.UserRepository;
import com.priyanshu.documents.document_service.security.AccountSecrets;

@Service
public class PasswordRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private final UserRepository users;
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    private final String frontendUrl;
    private final String smtpHost;

    public PasswordRecoveryService(UserRepository users, ObjectProvider<JavaMailSender> mail,
            @Value("${docnest.mail.from:}") String from,
            @Value("${docnest.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.users = users;
        this.mail = mail;
        this.from = from;
        this.smtpHost = smtpHost;
        URI uri = URI.create(frontendUrl);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("FRONTEND_URL must use HTTPS (HTTP is allowed only for localhost).");
        }
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public boolean isAvailable() { return !smtpHost.isBlank() && !from.isBlank() && mail.getIfAvailable() != null; }

    @Async("recoveryExecutor")
    @Transactional
    public void deliver(String normalizedEmail) {
        if (!isAvailable()) return;
        users.findByEmailForUpdate(normalizedEmail).ifPresent(user -> {
            String token = AccountSecrets.randomToken();
            user.setPasswordReset(AccountSecrets.hash(token), Instant.now().plus(Duration.ofMinutes(15)));
            users.save(user);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("Reset your DocNest password");
            message.setText("We received a request to reset your DocNest password.\n\n"
                    + frontendUrl + "/reset-password#token=" + token
                    + "\n\nThis link expires in 15 minutes and can be used once. "
                    + "If you did not request it, ignore this email. Your password has not changed.");
            try {
                mail.getObject().send(message);
            } catch (org.springframework.mail.MailException exception) {
                user.setPasswordReset(null, null);
                users.save(user);
                LOG.warn("Password recovery delivery failed. Check the SMTP configuration.");
            }
        });
    }
}
