package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationRequest;
import com.backup_manager.application.dto.CronValidationResponse;
import com.backup_manager.application.dto.MutationResponse;
import com.backup_manager.application.dto.ScheduledBackupRequest;
import com.backup_manager.application.dto.ScheduledBackupResponse;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.CronValidationService;
import com.backup_manager.application.service.DynamicSchedulerService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.event.BackupScheduledEvent;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/backup/config")
public class BackupConfigController {

    private static final Logger logger = LoggerFactory.getLogger(BackupConfigController.class);

    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledBackupRepository repository;
    private final DynamicSchedulerService dynamicSchedulerService;
    private final BackupRequestValidationService backupRequestValidationService;
    private final CronValidationService cronValidationService;
    private final SecurityAuditService securityAuditService;

    public BackupConfigController(ApplicationEventPublisher eventPublisher, ScheduledBackupRepository repository,
                                  DynamicSchedulerService dynamicSchedulerService,
                                  BackupRequestValidationService backupRequestValidationService,
                                  CronValidationService cronValidationService,
                                  SecurityAuditService securityAuditService) {
        this.eventPublisher = eventPublisher;
        this.repository = repository;
        this.dynamicSchedulerService = dynamicSchedulerService;
        this.backupRequestValidationService = backupRequestValidationService;
        this.cronValidationService = cronValidationService;
        this.securityAuditService = securityAuditService;
    }

    @PostMapping
    public ResponseEntity<Object> createOrUpdate(@Valid @RequestBody ScheduledBackupRequest request) {
        try {
            backupRequestValidationService.validateSchedulableRequest(
                    request.getSources(),
                    request.getDestinations()
            );

            CronValidationResponse validation = cronValidationService.validateCronExpression(request.getCronExpression());

            if (!validation.isValid()) {
                securityAuditService.recordFailure(
                        request.getId() == null ? "scheduler.config.create" : "scheduler.config.update",
                        "scheduled_backup_config",
                        "invalid_cron_expression",
                        auditConfigDetails(request.getId(), request.getName())
                );
                return ResponseEntity.badRequest().body(
                        ApiErrorResponse.of(
                                HttpStatus.BAD_REQUEST,
                                "Expressao cron invalida",
                                "invalid_cron_expression",
                                validation.getErrorMessage(),
                                "/api/backup/config"
                        )
                );
            }

            ScheduledBackupEntity config = resolveEntityForSave(request);
            ScheduledBackupEntity saved = repository.save(config);
            dynamicSchedulerService.refreshAllTasks();

            LocalDateTime nextExecution = cronValidationService.calculateNextExecution(saved.getCronExpression());
            eventPublisher.publishEvent(new BackupScheduledEvent(
                    saved.getId(),
                    saved.getName(),
                    saved.getSources(),
                    saved.getDestinations(),
                    nextExecution,
                    saved.getCronExpression()
            ));

            ScheduledBackupResponse response = ScheduledBackupResponse.fromEntity(saved, nextExecution);

            logger.info("Configuracao de backup salva e agendada: {}", saved.getName());
            securityAuditService.recordSuccess(
                    request.getId() == null ? "scheduler.config.create" : "scheduler.config.update",
                    "scheduled_backup_config",
                    Map.of("configId", saved.getId(), "enabled", saved.isEnabled(), "name", saved.getName())
            );
            return ResponseEntity.ok(
                    MutationResponse.success(
                            response,
                            "Configuracao de backup salva com sucesso",
                            validation.getDescription()
                    )
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Falha de validacao ao salvar configuracao: {}", e.getMessage());
            securityAuditService.recordFailure(
                    request.getId() == null ? "scheduler.config.create" : "scheduler.config.update",
                    "scheduled_backup_config",
                    "validation_failed",
                    auditConfigDetails(request.getId(), request.getName())
            );
            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    "scheduler_config_validation_failed",
                    "/api/backup/config"
            );
        } catch (Exception e) {
            logger.error("Erro ao salvar configuracao", e);
            securityAuditService.recordFailure(
                    request.getId() == null ? "scheduler.config.create" : "scheduler.config.update",
                    "scheduled_backup_config",
                    "internal_error",
                    auditConfigDetails(request.getId(), request.getName())
            );
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nao foi possivel salvar a configuracao de backup.",
                    "scheduler_config_save_failed",
                    "/api/backup/config"
            );
        }
    }

    @GetMapping
    public ResponseEntity<Object> listAll() {
        try {
            List<ScheduledBackupEntity> configs = repository.findAll();

            List<ScheduledBackupResponse> enrichedConfigs = configs.stream()
                    .map(this::toResponse)
                    .toList();

            return ResponseEntity.ok(CollectionResponse.of(enrichedConfigs));

        } catch (Exception e) {
            logger.error("Erro ao listar configuracoes", e);
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nao foi possivel listar as configuracoes de backup.",
                    "scheduler_config_list_failed",
                    "/api/backup/config"
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        Optional<ScheduledBackupEntity> config = repository.findById(id);

        if (config.isEmpty()) {
            return errorResponse(
                    HttpStatus.NOT_FOUND,
                    "Configuracao de backup nao encontrada.",
                    "scheduler_config_not_found",
                    "/api/backup/config/" + id
            );
        }

        return ResponseEntity.ok(toResponse(config.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        try {
            if (!repository.existsById(id)) {
                securityAuditService.recordFailure(
                        "scheduler.config.delete",
                        "scheduled_backup_config",
                        "config_not_found",
                        Map.of("configId", id)
                );
                return errorResponse(
                        HttpStatus.NOT_FOUND,
                        "Configuracao de backup nao encontrada.",
                        "scheduler_config_not_found",
                        "/api/backup/config/" + id
                );
            }

            repository.deleteById(id);
            dynamicSchedulerService.refreshAllTasks();

            logger.info("Configuracao de backup ID {} removida", id);
            securityAuditService.recordSuccess("scheduler.config.delete", "scheduled_backup_config", Map.of("configId", id));
            return ResponseEntity.ok(MutationResponse.success(null, "Configuracao de backup removida com sucesso"));
        } catch (Exception e) {
            logger.error("Erro ao remover configuracao de backup {}", id, e);
            securityAuditService.recordFailure(
                    "scheduler.config.delete",
                    "scheduled_backup_config",
                    "internal_error",
                    Map.of("configId", id)
            );
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nao foi possivel remover a configuracao de backup.",
                    "scheduler_config_delete_failed",
                    "/api/backup/config/" + id
            );
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Object> toggleEnabled(@PathVariable Long id) {
        try {
            Optional<ScheduledBackupEntity> configOpt = repository.findById(id);

            if (configOpt.isEmpty()) {
                securityAuditService.recordFailure(
                        "scheduler.config.toggle",
                        "scheduled_backup_config",
                        "config_not_found",
                        Map.of("configId", id)
                );
                return errorResponse(
                        HttpStatus.NOT_FOUND,
                        "Configuracao de backup nao encontrada.",
                        "scheduler_config_not_found",
                        "/api/backup/config/" + id + "/toggle"
                );
            }

            ScheduledBackupEntity config = configOpt.get();
            config.setEnabled(!config.isEnabled());

            ScheduledBackupEntity saved = repository.save(config);
            dynamicSchedulerService.refreshAllTasks();

            logger.info("Agendamento ID {} {}", id, saved.isEnabled() ? "ativado" : "desativado");
            securityAuditService.recordSuccess(
                    "scheduler.config.toggle",
                    "scheduled_backup_config",
                    Map.of("configId", id, "enabled", saved.isEnabled())
            );

            return ResponseEntity.ok(
                    MutationResponse.success(
                            toResponse(saved),
                            saved.isEnabled() ? "Agendamento ativado" : "Agendamento desativado"
                    )
            );

        } catch (Exception e) {
            logger.error("Erro ao alternar status do agendamento {}", id, e);
            securityAuditService.recordFailure(
                    "scheduler.config.toggle",
                    "scheduled_backup_config",
                    "internal_error",
                    Map.of("configId", id)
            );
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erro interno ao atualizar o agendamento.",
                    "scheduler_config_toggle_failed",
                    "/api/backup/config/" + id + "/toggle"
            );
        }
    }

    @PostMapping("/validate-cron")
    public ResponseEntity<CronValidationResponse> validateCron(@Valid @RequestBody CronValidationRequest request) {
        String cronExpression = request.getCronExpression();

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    CronValidationResponse.of(
                            false,
                            null,
                            "Expressao cron nao pode estar vazia",
                            null
                    )
            );
        }

        CronValidationResponse validation = cronValidationService.validateCronExpression(cronExpression);
        return ResponseEntity.ok(validation);
    }

    @GetMapping("/cron-templates")
    public ResponseEntity<CollectionResponse<CronTemplateResponse>> getCronTemplates() {
        return ResponseEntity.ok(
                CollectionResponse.of(cronValidationService.getCronTemplates().values().stream().toList())
        );
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message) {
        return errorResponse(status, message, null, null);
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status,
                                                 String message,
                                                 String code,
                                                 String path) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message, code, null, path));
    }

    private ScheduledBackupResponse toResponse(ScheduledBackupEntity entity) {
        LocalDateTime nextExecution = cronValidationService.calculateNextExecution(entity.getCronExpression());
        return ScheduledBackupResponse.fromEntity(entity, nextExecution);
    }

    private Map<String, Object> auditConfigDetails(Long configId, String name) {
        return Map.of(
                "configId", configId == null ? "new" : configId,
                "name", name == null ? "unnamed" : name
        );
    }

    private ScheduledBackupEntity resolveEntityForSave(ScheduledBackupRequest request) {
        ScheduledBackupEntity entity = request.getId() == null
                ? new ScheduledBackupEntity()
                : repository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Configuracao de backup nao encontrada para atualizacao."));

        // Preserva campos operacionais do agendamento em updates e limita o payload aos campos editaveis.
        entity.setName(request.getName());
        entity.setSources(request.getSources());
        entity.setDestinations(request.getDestinations());
        entity.setCronExpression(request.getCronExpression());

        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getId() == null) {
            entity.setEnabled(true);
        }

        return entity;
    }
}
