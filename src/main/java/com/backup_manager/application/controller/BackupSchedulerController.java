package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.SchedulerStatus;
import com.backup_manager.application.service.BackupScheduler;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backup/scheduler")
public class BackupSchedulerController {

    private static final Logger logger = LoggerFactory.getLogger(BackupSchedulerController.class);

    private final BackupScheduler backupScheduler;

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
                "Agendamento de Backups%nStatus: %s%nExpressao Cron: %s%nFuso Horario: %s%nConfiguracoes: %d total, %d ativas%nExecucoes recentes: %d",
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
            @Valid @RequestBody BackupRequest request) {

        try {
            if (minutesFromNow <= 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Minutos devem ser maior que 0");
                error.put("success", false);
                return ResponseEntity.badRequest().body(error);
            }

            String backupName = "Backup Agendado";
            Long taskId = backupScheduler.scheduleOneTimeBackup(request, minutesFromNow, backupName);

            LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(minutesFromNow);

            logger.info("Backup agendado ID {} para {}", taskId, scheduledTime);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Backup agendado com sucesso");
            response.put("taskId", taskId);
            response.put("scheduledTime", scheduledTime.toString());
            response.put("backupName", backupName);
            response.put("cancelUrl", "/api/backup/scheduler/schedule/" + taskId + "/cancel");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            logger.error("Erro ao agendar backup: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Erro interno ao agendar backup");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @DeleteMapping("/schedule/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelScheduledBackup(@PathVariable Long taskId) {
        try {
            boolean cancelled = backupScheduler.cancelScheduledBackup(taskId);

            if (cancelled) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Backup agendado cancelado com sucesso");
                response.put("taskId", taskId);
                response.put("timestamp", LocalDateTime.now().toString());

                logger.info("Backup ID {} cancelado pelo usuario", taskId);
                return ResponseEntity.ok(response);
            }

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Tarefa nao encontrada ou ja executada/cancelada");
            error.put("taskId", taskId);

            logger.warn("Tentativa de cancelar tarefa inexistente: {}", taskId);
            return ResponseEntity.status(404).body(error);
        } catch (Exception e) {
            logger.error("Erro ao cancelar backup {}: {}", taskId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Erro interno ao cancelar backup");
            error.put("taskId", taskId);
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/schedule/pending")
    public ResponseEntity<Map<String, Object>> getPendingScheduledBackups() {
        try {
            Map<Long, Map<String, Object>> pendingTasks = backupScheduler.getPendingScheduledBackups();

            Map<String, Object> response = new HashMap<>();
            response.put("count", pendingTasks.size());
            response.put("pendingTasks", pendingTasks);
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("success", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Erro ao listar backups pendentes: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Erro interno ao listar backups pendentes");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/execute-now")
    public ResponseEntity<Map<String, Object>> executeNow(@Valid @RequestBody BackupRequest request) {
        try {
            String backupName = "Backup Imediato " + LocalDateTime.now();
            logger.info("Executando backup imediato: {}", backupName);

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
            error.put("error", "Falha ao executar backup imediato.");

            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            SchedulerStatus status = backupScheduler.getSchedulerStatus();

            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now().toString());
            health.put("schedulerEnabled", status.isEnabled());
            health.put("activeConfigurations", status.getEnabledConfigurations());
            health.put("recentExecutions", status.getRecentExecutions());
            health.put("service", "backup-scheduler");

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("status", "DOWN");
            error.put("error", "Falha na verificacao do scheduler.");
            error.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(503).body(error);
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Desligando controller do scheduler...");
    }
}
