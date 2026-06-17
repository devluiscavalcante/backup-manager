package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationControllerTests {

    @Test
    void getSettingsShouldReturnStructuredInternalServerErrorWhenPropertiesAreInvalid() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEmail(null);
        NotificationController controller = new NotificationController(
                properties,
                mock(EmailNotificationService.class),
                mock(SecurityAuditService.class)
        );

        ResponseEntity<Object> response = controller.getSettings();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel carregar as configuracoes de notificacao.");
        assertThat(body.getCode()).isEqualTo("notification_settings_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/notifications/settings");
        assertThat(body.getDetails()).isNull();
    }

    @Test
    void sendTestEmailShouldReturnStructuredInternalServerErrorWhenEmailServiceFailsUnexpectedly() {
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        NotificationController controller = new NotificationController(
                new NotificationProperties(),
                emailService,
                mock(SecurityAuditService.class)
        );

        when(emailService.sendTestEmail()).thenThrow(new RuntimeException("smtp_unavailable"));

        ResponseEntity<Object> response = controller.sendTestEmail();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Erro interno ao enviar email de teste.");
        assertThat(body.getCode()).isEqualTo("notification_test_email_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/notifications/test");
        assertThat(body.getDetails()).isNull();
    }
}
