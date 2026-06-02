package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.NotificationSettingsResponse;
import com.backup_manager.application.dto.MutationResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.infrastructure.config.NotificationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/backup/notifications")
public class NotificationController {

    private static final String NOTIFICATION_TEST_PATH = "/api/backup/notifications/test";

    private final NotificationProperties properties;
    private final EmailNotificationService emailService;
    private final SecurityAuditService securityAuditService;

    public NotificationController(NotificationProperties properties,
                                  EmailNotificationService emailService,
                                  SecurityAuditService securityAuditService) {
        this.properties = properties;
        this.emailService = emailService;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping("/settings")
    public ResponseEntity<MutationResponse<NotificationSettingsResponse>> getSettings() {
        NotificationProperties.Email email = properties.getEmail();
        return ResponseEntity.ok(MutationResponse.success(
                new NotificationSettingsResponse(
                        properties.isEnabled(),
                        email.isEnabled(),
                        email.getFrom(),
                        email.getRecipients() != null ? email.getRecipients().size() : 0,
                        email.isNotifyOnSuccess(),
                        email.isNotifyOnFailure(),
                        email.isNotifyOnCancellation(),
                        email.isNotifyOnStarted(),
                        email.isNotifyOnScheduled()
                ),
                "Configuracoes de notificacao carregadas com sucesso"
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<Object> sendTestEmail() {
        boolean success = emailService.sendTestEmail();

        if (success) {
            securityAuditService.recordSuccess("notifications.test_email", "email_notification", Map.of());
            return ResponseEntity.ok(OperationResponse.success("Email de teste enviado"));
        }

        securityAuditService.recordFailure("notifications.test_email", "email_notification", "delivery_failed", Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falha ao enviar email de teste.",
                        "notification_test_email_failed",
                        null,
                        NOTIFICATION_TEST_PATH
                )
        );
    }
}
