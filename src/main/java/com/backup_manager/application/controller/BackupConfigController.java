package com.backup_manager.application.controller;

import com.backup_manager.application.service.DynamicSchedulerService;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup/config")
public class BackupConfigController {

    private static final Logger logger = LoggerFactory.getLogger(BackupConfigController.class);

    private final ScheduledBackupRepository repository;
    private final DynamicSchedulerService dynamicSchedulerService;

    public BackupConfigController(ScheduledBackupRepository repository,
                                  DynamicSchedulerService dynamicSchedulerService) {
        this.repository = repository;
        this.dynamicSchedulerService = dynamicSchedulerService;
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdate(@RequestBody ScheduledBackupEntity config) {
        try {
            ScheduledBackupEntity saved = repository.save(config);

            dynamicSchedulerService.refreshAllTasks();

            logger.info("Nova configuração de backup salva e agendada: {}", saved.getName());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Erro ao salvar configuração: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<ScheduledBackupEntity>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduledBackupEntity> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
}