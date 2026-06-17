package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.NotificationSettingsResponse;
import com.backup_manager.application.dto.MutationResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.infrastructure.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private static final String NOTIFICATION_SETTINGS_PATH = "/api/backup/notifications/settings";
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
    public ResponseEntity<Object> getSettings() {
        try {
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
        } catch (Exception e) {
            logger.error("Erro ao carregar configuracoes de notificacao", e);
            return apiError(
                    "Nao foi possivel carregar as configuracoes de notificacao.",
                    "notification_settings_failed",
                    NOTIFICATION_SETTINGS_PATH
            );
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Object> sendTestEmail() {
        try {
            boolean success = emailService.sendTestEmail();

            if (success) {
                securityAuditService.recordSuccess("notifications.test_email", "email_notification", Map.of());
                return ResponseEntity.ok(OperationResponse.success("Email de teste enviado"));
            }

            securityAuditService.recordFailure("notifications.test_email", "email_notification", "delivery_failed", Map.of());
            return apiError(
                    "Falha ao enviar email de teste.",
                    "notification_test_email_failed",
                    NOTIFICATION_TEST_PATH
            );
        } catch (Exception e) {
            logger.error("Erro interno ao enviar email de teste", e);
            securityAuditService.recordFailure("notifications.test_email", "email_notification", "internal_error", Map.of());
            return apiError(
                    "Erro interno ao enviar email de teste.",
                    "notification_test_email_failed",
                    NOTIFICATION_TEST_PATH
            );
        }
    }

    private ResponseEntity<Object> apiError(String message, String code, String path) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        message,
                        code,
                        null,
                        path
                )
        );
    }
}
