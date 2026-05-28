package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.HealthStatusResponse;
import com.backup_manager.application.dto.MutationResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.dto.PendingScheduledBackupResponse;
import com.backup_manager.application.dto.SchedulerHealthSummary;
import com.backup_manager.application.dto.SchedulerInfoResponse;
import com.backup_manager.application.dto.SchedulerStatus;
import com.backup_manager.application.service.BackupScheduler;
import com.backup_manager.application.service.SecurityAuditService;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup/scheduler")
public class BackupSchedulerController {

    private static final Logger logger = LoggerFactory.getLogger(BackupSchedulerController.class);

    private final BackupScheduler backupScheduler;
    private final SecurityAuditService securityAuditService;

    public BackupSchedulerController(BackupScheduler backupScheduler,
                                     SecurityAuditService securityAuditService) {
        this.backupScheduler = backupScheduler;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping("/status")
    public ResponseEntity<MutationResponse<SchedulerStatus>> getSchedulerStatus() {
        SchedulerStatus status = backupScheduler.getSchedulerStatus();
        return ResponseEntity.ok(MutationResponse.success(status, "Status do scheduler carregado com sucesso"));
    }

    @GetMapping("/info")
    public ResponseEntity<MutationResponse<SchedulerInfoResponse>> getSchedulerInfo() {
        SchedulerStatus status = backupScheduler.getSchedulerStatus();

        return ResponseEntity.ok(MutationResponse.success(
                new SchedulerInfoResponse(
                        status.isEnabled() ? "ATIVO" : "INATIVO",
                        status.isEnabled(),
                        status.getCronExpression(),
                        status.getTimeZone(),
                        status.getTotalConfigurations(),
                        status.getEnabledConfigurations(),
                        status.getRecentExecutions()
                ),
                "Resumo do scheduler carregado com sucesso"
        ));
    }

    @PostMapping("/schedule-once")
    public ResponseEntity<OperationResponse> scheduleOneTimeBackup(
            @RequestParam int minutesFromNow,
            @Valid @RequestBody BackupRequest request) {

        try {
            if (minutesFromNow <= 0) {
                return ResponseEntity.badRequest().body(OperationResponse.error("Minutos devem ser maior que 0"));
            }

            String backupName = "Backup Agendado";
            Long taskId = backupScheduler.scheduleOneTimeBackup(request, minutesFromNow, backupName);

            LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(minutesFromNow);

            logger.info("Backup agendado ID {} para {}", taskId, scheduledTime);
            securityAuditService.recordSuccess(
                    "scheduler.schedule_once",
                    "scheduled_backup_task",
                    Map.of("taskId", taskId, "minutesFromNow", minutesFromNow, "backupName", backupName)
            );

            return ResponseEntity.ok(OperationResponse.scheduled(
                    "Backup agendado com sucesso",
                    taskId,
                    backupName,
                    scheduledTime,
                    "/api/backup/scheduler/schedule/" + taskId + "/cancel"
            ));

        } catch (IllegalArgumentException e) {
            logger.warn("Falha de validacao ao agendar backup: {}", e.getMessage());
            securityAuditService.recordFailure(
                    "scheduler.schedule_once",
                    "scheduled_backup_task",
                    "validation_failed",
                    Map.of("minutesFromNow", minutesFromNow)
            );
            return errorResponse(HttpStatus.BAD_REQUEST, "Nao foi possivel agendar o backup com os dados informados.");

        } catch (Exception e) {
            logger.error("Erro ao agendar backup: {}", e.getMessage(), e);
            securityAuditService.recordFailure(
                    "scheduler.schedule_once",
                    "scheduled_backup_task",
                    "internal_error",
                    Map.of("minutesFromNow", minutesFromNow)
            );
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao agendar backup.");
        }
    }

    @DeleteMapping("/schedule/{taskId}/cancel")
    public ResponseEntity<OperationResponse> cancelScheduledBackup(@PathVariable Long taskId) {
        try {
            boolean cancelled = backupScheduler.cancelScheduledBackup(taskId);

            if (cancelled) {
                logger.info("Backup ID {} cancelado pelo usuario", taskId);
                securityAuditService.recordSuccess("scheduler.cancel", "scheduled_backup_task", Map.of("taskId", taskId));
                return ResponseEntity.ok(OperationResponse.success("Backup agendado cancelado com sucesso", taskId));
            }

            logger.warn("Tentativa de cancelar tarefa inexistente: {}", taskId);
            securityAuditService.recordFailure("scheduler.cancel", "scheduled_backup_task", "task_not_found_or_completed",
                    Map.of("taskId", taskId));
            return ResponseEntity.status(404)
                    .body(OperationResponse.error("Tarefa nao encontrada ou ja executada/cancelada", taskId));
        } catch (Exception e) {
            logger.error("Erro ao cancelar backup {}: {}", taskId, e.getMessage(), e);
            securityAuditService.recordFailure("scheduler.cancel", "scheduled_backup_task", "internal_error",
                    Map.of("taskId", taskId));
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao cancelar backup.");
        }
    }

    @GetMapping("/schedule/pending")
    public ResponseEntity<CollectionResponse<PendingScheduledBackupResponse>> getPendingScheduledBackups() {
        try {
            List<PendingScheduledBackupResponse> pendingTasks = backupScheduler.getPendingScheduledBackups();

            return ResponseEntity.ok(CollectionResponse.of(pendingTasks));
        } catch (Exception e) {
            logger.error("Erro ao listar backups pendentes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CollectionResponse.empty("Erro interno ao listar backups pendentes."));
        }
    }

    @PostMapping("/execute-now")
    public ResponseEntity<OperationResponse> executeNow(@Valid @RequestBody BackupRequest request) {
        try {
            String backupName = "Backup Imediato " + LocalDateTime.now();
            logger.info("Executando backup imediato: {}", backupName);

            backupScheduler.executeBackupWithRequest(request, backupName);
            securityAuditService.recordSuccess("scheduler.execute_now", "backup_request", Map.of("backupName", backupName));

            return ResponseEntity.ok(OperationResponse.namedSuccess("Backup executado com sucesso", backupName));

        } catch (Exception e) {
            logger.error("Erro ao executar backup imediato: {}", e.getMessage(), e);
            securityAuditService.recordFailure("scheduler.execute_now", "backup_request", "execution_failed", Map.of());
            return errorResponse(HttpStatus.BAD_REQUEST, "Falha ao executar backup imediato.");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<HealthStatusResponse> healthCheck() {
        try {
            SchedulerStatus status = backupScheduler.getSchedulerStatus();

            return ResponseEntity.ok(
                    HealthStatusResponse.of(
                            "UP",
                            "backup-scheduler",
                            null,
                            null,
                            SchedulerHealthSummary.from(status)
                    )
            );
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());

            return ResponseEntity.status(503)
                    .body(HealthStatusResponse.of(
                            "DOWN",
                            "backup-scheduler",
                            null,
                            "Falha na verificacao do scheduler.",
                            SchedulerHealthSummary.down()
                    ));
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Desligando controller do scheduler...");
    }

    private ResponseEntity<OperationResponse> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(OperationResponse.error(message));
    }
}
