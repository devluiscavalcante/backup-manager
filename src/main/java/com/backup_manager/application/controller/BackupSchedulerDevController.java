package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.service.BackupScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/api/backup/scheduler")
public class BackupSchedulerDevController {

    private static final Logger logger = LoggerFactory.getLogger(BackupSchedulerDevController.class);

    private final BackupScheduler backupScheduler;

    public BackupSchedulerDevController(BackupScheduler backupScheduler) {
        this.backupScheduler = backupScheduler;
    }

    @PostMapping("/test-5min")
    public ResponseEntity<Map<String, Object>> test5MinuteBackup() {
        return scheduleDevBackup(5, "C:/Temp/origem", "C:/Temp/destino");
    }

    @PostMapping("/test-quick")
    public ResponseEntity<Map<String, Object>> testQuickBackup() {
        return scheduleDevBackup(1, "C:/Temp/test-origem", "C:/Temp/test-destino");
    }

    private ResponseEntity<Map<String, Object>> scheduleDevBackup(int minutesFromNow,
                                                                  String source,
                                                                  String destination) {
        try {
            BackupRequest request = new BackupRequest();
            request.setSources(java.util.List.of(source));
            request.setDestination(java.util.List.of(destination));

            Long taskId = backupScheduler.scheduleOneTimeBackup(request, minutesFromNow, "Backup Dev");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("scheduledTime", LocalDateTime.now().plusMinutes(minutesFromNow).toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Erro ao executar endpoint de teste do scheduler: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Erro no endpoint de teste do scheduler");
            return ResponseEntity.badRequest().body(error);
        }
    }
}
