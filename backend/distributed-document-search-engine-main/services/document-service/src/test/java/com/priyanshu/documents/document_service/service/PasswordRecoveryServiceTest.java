package com.priyanshu.documents.document_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import com.priyanshu.documents.document_service.repository.UserRepository;
import com.priyanshu.documents.document_service.entity.User;

class PasswordRecoveryServiceTest {
    @SuppressWarnings("unchecked")
    @Test void sendsEachResetToTheRegisteredUserRatherThanTheConfiguredSender() {
        var users = mock(UserRepository.class);
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var mail = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        when(provider.getObject()).thenReturn(mail);
        String sender = "docnest-sender@163.com";
        // Synthetic addresses; mocked JavaMailSender never connects or sends real mail.
        var recipients = java.util.List.of("docnest-test-user@gmail.com", "docnest-test-user@qq.com", "docnest-test-user@outlook.com");
        var accounts = new java.util.ArrayList<User>();
        var service = new PasswordRecoveryService(users, provider, sender, "http://localhost:5173", "smtp.163.com");
        for (String recipient : recipients) {
            User user = new User(recipient, "existing-password-hash");
            accounts.add(user);
            when(users.findByEmailForUpdate(recipient)).thenReturn(Optional.of(user));
            service.deliver(recipient);
        }
        var messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(3)).send(messages.capture());
        var resetHashes = new java.util.HashSet<String>();
        for (int index = 0; index < recipients.size(); index++) {
            var message = messages.getAllValues().get(index);
            assertThat(message.getFrom()).isEqualTo(sender);
            assertThat(message.getTo()).containsExactly(recipients.get(index)).doesNotContain(sender);
            String rawToken = message.getText().split("#token=")[1].split("\\s")[0];
            assertThat(accounts.get(index).getResetTokenHash())
                    .isEqualTo(com.priyanshu.documents.document_service.security.AccountSecrets.hash(rawToken));
            assertThat(accounts.get(index).getPasswordHash()).isEqualTo("existing-password-hash");
            resetHashes.add(accounts.get(index).getResetTokenHash());
        }
        assertThat(resetHashes).hasSize(recipients.size());
    }

    @SuppressWarnings("unchecked")
    @Test void missingSmtpIsExplicitlyUnavailable() {
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var service = new PasswordRecoveryService(mock(UserRepository.class), provider, "", "http://localhost:5173", "");
        assertThat(service.isAvailable()).isFalse();
        assertThatThrownBy(() -> new PasswordRecoveryService(mock(UserRepository.class), provider,
                "test@example.test", "http://public.example", "smtp.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    @Test void deliveryFailureClearsTokenAndUnknownAccountSendsNothing() {
        var users = mock(UserRepository.class);
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var mail = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        when(provider.getObject()).thenReturn(mail);
        User user = new User("member@example.test", "hash");
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByEmailForUpdate("unknown@example.test")).thenReturn(Optional.empty());
        doThrow(new MailSendException("provider-specific-sensitive-detail")).when(mail).send(any(SimpleMailMessage.class));
        var service = new PasswordRecoveryService(users, provider, "docnest@example.test", "http://localhost:5173", "smtp.example.test");
        service.deliver(user.getEmail());
        assertThat(user.getResetTokenHash()).isNull();
        assertThat(user.getResetExpiresAt()).isNull();
        service.deliver("unknown@example.test");
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }
}
