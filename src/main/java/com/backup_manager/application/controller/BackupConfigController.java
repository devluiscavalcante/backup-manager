package com.backup_manager.application.controller;

import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import com.backup_manager.application.service.CronValidationService;
import com.backup_manager.application.service.DynamicSchedulerService;
import com.backup_manager.domain.event.BackupScheduledEvent;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CronValidationService cronValidationService;

    public BackupConfigController(ApplicationEventPublisher eventPublisher, ScheduledBackupRepository repository,
                                  DynamicSchedulerService dynamicSchedulerService,
                                  CronValidationService cronValidationService) {
        this.eventPublisher = eventPublisher;
        this.repository = repository;
        this.dynamicSchedulerService = dynamicSchedulerService;
        this.cronValidationService = cronValidationService;
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdate(@RequestBody ScheduledBackupEntity config) {
        try {
            CronValidationResponse validation = cronValidationService.validateCronExpression(config.getCronExpression());

            if (!validation.isValid()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Expressão cron inválida");
                error.put("message", validation.getErrorMessage());
                error.put("cronExpression", config.getCronExpression());
                return ResponseEntity.badRequest().body(error);
            }

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
            response.put("id", saved.getId());
            response.put("name", saved.getName());
            response.put("cronExpression", saved.getCronExpression());
            response.put("enabled", saved.isEnabled());
            response.put("lastExecution", saved.getLastExecution());
            response.put("nextExecution", cronValidationService.calculateNextExecution(saved.getCronExpression()));
            response.put("cronDescription", validation.getDescription());

            logger.info("Configuração de backup salva e agendada: {}", saved.getName());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Erro ao salvar configuração: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        try {
            List<ScheduledBackupEntity> configs = repository.findAll();

            List<Map<String, Object>> enrichedConfigs = configs.stream().map(config -> {
                Map<String, Object> enriched = new HashMap<>();
                enriched.put("id", config.getId());
                enriched.put("name", config.getName());
                enriched.put("sources", config.getSources());
                enriched.put("destinations", config.getDestinations());
                enriched.put("cronExpression", config.getCronExpression());
                enriched.put("enabled", config.isEnabled());
                enriched.put("lastExecution", config.getLastExecution());
                enriched.put("nextExecution", cronValidationService.calculateNextExecution(config.getCronExpression()));
                enriched.put("createdAt", config.getCreatedAt());
                enriched.put("updatedAt", config.getUpdatedAt());
                return enriched;
            }).toList();

            return ResponseEntity.ok(enrichedConfigs);

        } catch (Exception e) {
            logger.error("Erro ao listar configurações: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<ScheduledBackupEntity> config = repository.findById(id);

        if (config.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ScheduledBackupEntity entity = config.get();

        Map<String, Object> response = new HashMap<>();
        response.put("id", entity.getId());
        response.put("name", entity.getName());
        response.put("sources", entity.getSources());
        response.put("destinations", entity.getDestinations());
        response.put("cronExpression", entity.getCronExpression());
        response.put("enabled", entity.isEnabled());
        response.put("lastExecution", entity.getLastExecution());
        response.put("nextExecution", cronValidationService.calculateNextExecution(entity.getCronExpression()));
        response.put("createdAt", entity.getCreatedAt());
        response.put("updatedAt", entity.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        repository.deleteById(id);
        dynamicSchedulerService.refreshAllTasks();

        logger.info("Configuração de backup ID {} removida", id);
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
            response.put("id", saved.getId());
            response.put("name", saved.getName());
            response.put("enabled", saved.isEnabled());
            response.put("message", saved.isEnabled() ? "Agendamento ativado" : "Agendamento desativado");

            logger.info("Agendamento ID {} {}", id, saved.isEnabled() ? "ativado" : "desativado");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Erro ao alternar status do agendamento {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
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
                            "Expressão cron não pode estar vazia",
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
}