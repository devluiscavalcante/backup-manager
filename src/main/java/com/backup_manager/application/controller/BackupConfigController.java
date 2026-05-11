package com.backup_manager.application.controller;

import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import com.backup_manager.application.dto.ScheduledBackupRequest;
import com.backup_manager.application.dto.ScheduledBackupResponse;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.CronValidationService;
import com.backup_manager.application.service.DynamicSchedulerService;
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
import java.util.HashMap;
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

    public BackupConfigController(ApplicationEventPublisher eventPublisher, ScheduledBackupRepository repository,
                                  DynamicSchedulerService dynamicSchedulerService,
                                  BackupRequestValidationService backupRequestValidationService,
                                  CronValidationService cronValidationService) {
        this.eventPublisher = eventPublisher;
        this.repository = repository;
        this.dynamicSchedulerService = dynamicSchedulerService;
        this.backupRequestValidationService = backupRequestValidationService;
        this.cronValidationService = cronValidationService;
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdate(@Valid @RequestBody ScheduledBackupRequest request) {
        try {
            backupRequestValidationService.validateSchedulableRequest(
                    request.getSources(),
                    request.getDestinations()
            );

            CronValidationResponse validation = cronValidationService.validateCronExpression(request.getCronExpression());

            if (!validation.isValid()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Expressao cron invalida");
                error.put("message", validation.getErrorMessage());
                error.put("cronExpression", request.getCronExpression());
                return ResponseEntity.badRequest().body(error);
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

            Map<String, Object> response = new HashMap<>();
            response.put("config", ScheduledBackupResponse.fromEntity(saved, nextExecution));
            response.put("cronDescription", validation.getDescription());

            logger.info("Configuracao de backup salva e agendada: {}", saved.getName());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Falha de validacao ao salvar configuracao: {}", e.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Erro ao salvar configuracao", e);
            return errorResponse(HttpStatus.BAD_REQUEST, "Nao foi possivel salvar a configuracao de backup.");
        }
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        try {
            List<ScheduledBackupEntity> configs = repository.findAll();

            List<ScheduledBackupResponse> enrichedConfigs = configs.stream()
                    .map(this::toResponse)
                    .toList();

            return ResponseEntity.ok(enrichedConfigs);

        } catch (Exception e) {
            logger.error("Erro ao listar configuracoes", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao listar configuracoes de backup.");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<ScheduledBackupEntity> config = repository.findById(id);

        if (config.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(config.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        repository.deleteById(id);
        dynamicSchedulerService.refreshAllTasks();

        logger.info("Configuracao de backup ID {} removida", id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleEnabled(@PathVariable Long id) {
        try {
            Optional<ScheduledBackupEntity> configOpt = repository.findById(id);

            if (configOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ScheduledBackupEntity config = configOpt.get();
            config.setEnabled(!config.isEnabled());

            ScheduledBackupEntity saved = repository.save(config);
            dynamicSchedulerService.refreshAllTasks();

            Map<String, Object> response = new HashMap<>();
            response.put("config", toResponse(saved));
            response.put("message", saved.isEnabled() ? "Agendamento ativado" : "Agendamento desativado");

            logger.info("Agendamento ID {} {}", id, saved.isEnabled() ? "ativado" : "desativado");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Erro ao alternar status do agendamento {}", id, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao atualizar o agendamento.");
        }
    }

    @PostMapping("/validate-cron")
    public ResponseEntity<CronValidationResponse> validateCron(@RequestBody Map<String, String> request) {
        String cronExpression = request.get("cronExpression");

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new CronValidationResponse(
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
    public ResponseEntity<Map<String, CronTemplateResponse>> getCronTemplates() {
        Map<String, CronTemplateResponse> templates = cronValidationService.getCronTemplates();
        return ResponseEntity.ok(templates);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return ResponseEntity.status(status).body(error);
    }

    private ScheduledBackupResponse toResponse(ScheduledBackupEntity entity) {
        LocalDateTime nextExecution = cronValidationService.calculateNextExecution(entity.getCronExpression());
        return ScheduledBackupResponse.fromEntity(entity, nextExecution);
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
