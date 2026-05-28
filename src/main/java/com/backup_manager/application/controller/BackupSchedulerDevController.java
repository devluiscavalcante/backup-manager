package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.service.BackupScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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
    public ResponseEntity<?> test5MinuteBackup() {
        return scheduleDevBackup(5, "C:/Temp/origem", "C:/Temp/destino", "/api/backup/scheduler/test-5min");
    }

    @PostMapping("/test-quick")
    public ResponseEntity<?> testQuickBackup() {
        return scheduleDevBackup(1, "C:/Temp/test-origem", "C:/Temp/test-destino", "/api/backup/scheduler/test-quick");
    }

    private ResponseEntity<?> scheduleDevBackup(int minutesFromNow,
                                                String source,
                                                String destination,
                                                String path) {
        try {
            BackupRequest request = new BackupRequest();
            request.setSources(java.util.List.of(source));
            request.setDestination(java.util.List.of(destination));

            Long taskId = backupScheduler.scheduleOneTimeBackup(request, minutesFromNow, "Backup Dev");

            return ResponseEntity.ok(OperationResponse.scheduled(
                    "Backup de desenvolvimento agendado com sucesso",
                    taskId,
                    "Backup Dev",
                    LocalDateTime.now().plusMinutes(minutesFromNow),
                    "/api/backup/scheduler/schedule/" + taskId + "/cancel"
            ));
        } catch (Exception e) {
            logger.error("Erro ao executar endpoint de teste do scheduler: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiErrorResponse.of(
                            HttpStatus.BAD_REQUEST,
                            "Erro no endpoint de teste do scheduler.",
                            "scheduler_dev_test_failed",
                            null,
                            path
                    )
            );
        }
    }
}
