package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.dto.BackupStatsResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;
    private final ProgressEmitter progressEmitter;
    private final BackupRepository backupRepository;
    private final BackupHistoryService historyService;

    public BackupController(BackupService backupService, ProgressEmitter progressEmitter,
                            BackupRepository backupRepository, BackupHistoryService historyService) {
        this.backupService = backupService;
        this.progressEmitter = progressEmitter;
        this.backupRepository = backupRepository;
        this.historyService = historyService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startBackup(@RequestBody BackupRequest request) {
        try {
            List<String> sources = request.getSources();
            List<String> destinations = request.getDestination();

            if (sources == null || destinations == null || sources.isEmpty() || destinations.isEmpty()) {
                return ResponseEntity.badRequest().body("As listas não podem estar vazias");
            }
            if (sources.size() != destinations.size()) {
                return ResponseEntity.badRequest().body("O número de origens deve ser igual ao número de destinos.");
            }

            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                String destination = destinations.get(i);

                backupService.validateSafePath(source);
                backupService.validateSafePath(destination);

                backupService.validatePathAndDriveSpace(source, destination);

                Optional<BackupTask> activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Já existe um backup ativo para este par origem/destino");
                    errorResponse.put("source", source);
                    errorResponse.put("destination", destination);
                    errorResponse.put("taskId", activeTask.get().getId());
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
                }
            }

            List<Long> taskIds = new ArrayList<>();

            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                String destination = destinations.get(i);

                Long taskId = backupService.runBackup(source, destination);

                if (taskId != null) {
                    taskIds.add(taskId);
                    logger.info("Backup iniciado com sucesso: taskId={}, source={}, destination={}",
                            taskId, source, destination);
                } else {
                    logger.warn("Falha ao iniciar backup: source={}, destination={}", source, destination);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Verificações concluídas. Backup(s) iniciado(s) com sucesso");
            response.put("taskIds", taskIds);

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Falha na validação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro inesperado: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getBackupHistory() {
        try {
            List<BackupTask> tasks = backupService.getAllTasks();
            List<BackupResponse> responseList = new ArrayList<>();

            for (BackupTask task : tasks) {
                String duration = "";
                if (task.getStartedAt() != null && task.getFinishedAt() != null) {
                    long seconds = java.time.Duration.between(task.getStartedAt(), task.getFinishedAt()).getSeconds();
                    duration = String.format("%02d:%02d:%02d",
                            seconds / 3600, (seconds % 3600) / 60, seconds % 60);
                }

                BackupResponse dto = new BackupResponse(
                        task.getSourcePath(),
                        task.getDestinationPath(),
                        task.getStatus(),
                        task.getErrorMessage(),
                        task.getFileCount(),
                        task.getTotalSizeMB(),
                        task.getStartedAt(),
                        task.getFinishedAt(),
                        task.getPausedAt(),
                        duration
                );

                responseList.add(dto);
            }

            return ResponseEntity.ok(responseList);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao listar histórico: " + e.getMessage());
        }
    }

    @GetMapping("/history/search")
    public ResponseEntity<?> searchHistory(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir
    ) {
        try {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Data inicial não pode ser posterior à data final"));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tamanho da página deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
            Page<BackupResponse> result = historyService.searchHistory(status, startDate, endDate, pageable);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Erro ao buscar histórico: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Erro ao buscar histórico: " + e.getMessage()));
        }
    }

    @GetMapping("/history/stats")
    public ResponseEntity<?> getStatistics() {
        try {
            BackupStatsResponse stats = historyService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Erro ao calcular estatísticas: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Erro ao calcular estatísticas: " + e.getMessage()));
        }
    }

    @GetMapping("/history/recent")
    public ResponseEntity<?> getRecentBackups(
            @RequestParam(defaultValue = "5") int limit
    ) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Limite deve estar entre 1 e 100"));
            }

            List<BackupResponse> recent = historyService.getRecentBackups(limit);
            return ResponseEntity.ok(recent);

        } catch (Exception e) {
            logger.error("Erro ao buscar backups recentes: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Erro ao buscar backups recentes: " + e.getMessage()));
        }
    }

    @GetMapping("/progress")
    public SseEmitter streamProgress() {
        return progressEmitter.createEmitter();
    }

    @PostMapping("/{taskId}/pause")
    public ResponseEntity<String> pauseBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.pauseBackup(taskId);
            if (success) {
                return ResponseEntity.ok("Backup pausado com sucesso");
            } else {
                return ResponseEntity.status(404).body("Tarefa não encontrada ou não pode ser pausada");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao pausar backup: " + e.getMessage());
        }
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<String> resumeBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.resumeBackup(taskId);
            if (success) {
                return ResponseEntity.ok("Backup retomado com sucesso");
            } else {
                return ResponseEntity.status(404).body("Tarefa não encontrada ou não pode ser retomada");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao retomar backup: " + e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<String> cancelBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.cancelBackup(taskId);
            if (success) {
                return ResponseEntity.ok("Backup cancelado com sucesso");
            } else {
                return ResponseEntity.status(404).body("Tarefa não encontrada ou não pode ser cancelada");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao cancelar backup: " + e.getMessage());
        }
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<?> getTaskStatus(@PathVariable Long taskId) {
        Optional<BackupTask> task = backupRepository.findById(taskId);
        if (task.isPresent()) {
            return ResponseEntity.ok(task.get());
        } else {
            return ResponseEntity.status(404).body("Tarefa não encontrada");
        }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveTasks() {
        List<BackupTask> allTasks = backupRepository.findAll();
        List<BackupTask> activeTasks = allTasks.stream()
                .filter(t -> t.getStatus() == Status.EM_ANDAMENTO ||
                        t.getStatus() == Status.PAUSADO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(activeTasks);
    }
}