package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.SchedulerStatus;
import com.backup_manager.application.service.BackupScheduler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/backup/scheduler")
public class BackupSchedulerController {

    private static final Logger logger = LoggerFactory.getLogger(BackupSchedulerController.class);

    private final BackupScheduler backupScheduler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public BackupSchedulerController(BackupScheduler backupScheduler) {
        this.backupScheduler = backupScheduler;
    }

    @GetMapping("/status")
    public ResponseEntity<SchedulerStatus> getSchedulerStatus() {
        SchedulerStatus status = backupScheduler.getSchedulerStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/info")
    public ResponseEntity<String> getSchedulerInfo() {
        SchedulerStatus status = backupScheduler.getSchedulerStatus();

        String info = String.format(
                "Agendamento de Backups\n" +
                        "────────────────────────────\n" +
                        "Status: %s\n" +
                        "Expressão Cron: %s\n" +
                        "Fuso Horário: %s\n" +
                        "Configurações: %d total, %d ativas\n" +
                        "Execuções recentes: %d",
                status.isEnabled() ? "ATIVO" : "INATIVO",
                status.getCronExpression(),
                status.getTimeZone(),
                status.getTotalConfigurations(),
                status.getEnabledConfigurations(),
                status.getRecentExecutions()
        );

        return ResponseEntity.ok(info);
    }

    @PostMapping("/schedule-once")
    public ResponseEntity<Map<String, Object>> scheduleOneTimeBackup(
            @RequestParam int minutesFromNow,
            @RequestBody BackupRequest request) {

        if (minutesFromNow <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Minutos devem ser maior que 0");
            return ResponseEntity.badRequest().body(error);
        }

        LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(minutesFromNow);
        String backupName = "Backup Agendado " + scheduledTime;

        logger.info("Agendando backup '{}' para daqui {} minutos", backupName, minutesFromNow);

        scheduler.schedule(() -> {
            try {
                logger.info("Executando backup agendado: {}", backupName);
                backupScheduler.executeBackupWithRequest(request, backupName);
                logger.info("Backup agendado concluído: {}", backupName);
            } catch (Exception e) {
                logger.error("Erro no backup agendado '{}': {}", backupName, e.getMessage(), e);
            }
        }, minutesFromNow, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Backup agendado com sucesso");
        response.put("scheduledTime", scheduledTime.toString());
        response.put("backupName", backupName);
        response.put("sources", request.getSources());
        response.put("destinations", request.getDestination());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute-now")
    public ResponseEntity<Map<String, Object>> executeNow(@RequestBody BackupRequest request) {
        try {
            String backupName = "Backup Imediato " + LocalDateTime.now();
            logger.info("⚡ Executando backup imediato: {}", backupName);

            backupScheduler.executeBackupWithRequest(request, backupName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Backup executado com sucesso");
            response.put("backupName", backupName);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Erro ao executar backup imediato: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/test-5min")
    public ResponseEntity<Map<String, Object>> test5MinuteBackup() {
        // Endpoint para teste
        BackupRequest request = new BackupRequest();
        request.setSources(java.util.List.of("C:/Temp/origem"));
        request.setDestination(java.util.List.of("C:/Temp/destino"));

        return scheduleOneTimeBackup(5, request);
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Desligando scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}