package com.backup_manager.application.controller;

import com.backup_manager.application.dto.NotificationSettingsResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.infrastructure.config.NotificationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backup/notifications")
public class NotificationController {

    private final NotificationProperties properties;
    private final EmailNotificationService emailService;

    public NotificationController(NotificationProperties properties,
                                  EmailNotificationService emailService) {
        this.properties = properties;
        this.emailService = emailService;
    }

    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingsResponse> getSettings() {
        NotificationProperties.Email email = properties.getEmail();
        return ResponseEntity.ok(new NotificationSettingsResponse(
                properties.isEnabled(),
                email.isEnabled(),
                email.getFrom(),
                email.getRecipients() != null ? email.getRecipients().size() : 0,
                email.isNotifyOnSuccess(),
                email.isNotifyOnFailure(),
                email.isNotifyOnCancellation(),
                email.isNotifyOnStarted(),
                email.isNotifyOnScheduled()
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<OperationResponse> sendTestEmail() {
        boolean success = emailService.sendTestEmail();

        if (success) {
            return ResponseEntity.ok(OperationResponse.success("Email de teste enviado"));
        }

        return ResponseEntity.status(500).body(OperationResponse.error("Falha ao enviar email de teste."));
    }
}
