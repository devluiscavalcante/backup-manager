package com.backup_manager.application.controller;

import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.infrastructure.config.NotificationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(properties);
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestEmail() {
        boolean success = emailService.sendTestEmail();

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email de teste enviado",
                    "recipients", properties.getEmail().getRecipients()
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Falha ao enviar email. Verifique configurações SMTP."
            ));
        }
    }
}