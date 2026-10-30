package com.backup_manager.application.service;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.config.NotificationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTests {

    private JavaMailSender mailSender;
    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom("backup@example.com");
        properties.getEmail().setRecipients(List.of("ops@example.com"));

        // Backoff de 1 ms para o teste nao esperar os 2 s reais entre tentativas.
        service = new EmailNotificationService(mailSender, properties, 1);
    }

    @Test
    void notificationShouldEscapeHtmlFromPathsAndErrorMessages() throws Exception {
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        doNothing().when(mailSender).send(message.capture());
        BackupTask task = new BackupTask();
        task.setId(1L);
        task.setSourcePath("C:/Dados/<img src=x onerror=alert(1)>");

        service.sendFailureNotification(task, "Falha em <script>alert(1)</script>.txt");

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.getValue().writeTo(raw);
        String body = raw.toString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("<img").doesNotContain("<script>");
        assertThat(body).contains("&lt;img").contains("&lt;script&gt;");
    }

    @Test
    void failureNotificationShouldRetryTransientErrorsUntilDelivered() {
        doThrow(new MailSendException("connection reset"))
                .doThrow(new MailSendException("connection reset"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        service.sendFailureNotification(new BackupTask(), "disco cheio");

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void failureNotificationShouldStopAfterMaxAttemptsWithoutPropagating() {
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendFailureNotification(new BackupTask(), "disco cheio"))
                .doesNotThrowAnyException();

        verify(mailSender, times(EmailNotificationService.MAX_DELIVERY_ATTEMPTS)).send(any(MimeMessage.class));
    }

    @Test
    void failureNotificationShouldNotRetryRejectedCredentials() {
        doThrow(new MailAuthenticationException("535 authentication failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendFailureNotification(new BackupTask(), "disco cheio"))
                .doesNotThrowAnyException();

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void regularNotificationsShouldSendOnceAndPropagateMailErrors() {
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendCancellationNotification(new BackupTask()))
                .isInstanceOf(MailException.class);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testEmailShouldReportDeliveryResult() {
        doNothing().when(mailSender).send(any(MimeMessage.class));
        assertThat(service.sendTestEmail()).isTrue();

        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));
        assertThat(service.sendTestEmail()).isFalse();
    }
}
