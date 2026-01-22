package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;
    private final ProgressEmitter progressEmitter;
    private final BackupRepository backupRepository;

    public BackupController(BackupService backupService, ProgressEmitter progressEmitter,
                            BackupRepository backupRepository) {
        this.backupService = backupService;
        this.progressEmitter = progressEmitter;
        this.backupRepository = backupRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startBackup(@RequestBody BackupRequest request) {
        List<String> sources = request.getSources();
        List<String> destinations = request.getDestination();

        if (sources == null || destinations == null || sources.isEmpty() || destinations.isEmpty()) {
            return ResponseEntity.badRequest().body("As listas não podem estar vazias");
        }
        if (sources.size() != destinations.size()) {
            return ResponseEntity.badRequest().body("O número de origens deve ser igual ao número de destinos.");
        }

        List<Long> taskIds = new ArrayList<>();

        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            String destination = destinations.get(i);

            Optional<BackupTask> activeTask = backupService.getActiveTask(source, destination);
            if (activeTask.isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Já existe um backup ativo para este par origem/destino");
                errorResponse.put("source", source);
                errorResponse.put("destination", destination);
                errorResponse.put("taskId", activeTask.get().getId());

                return ResponseEntity.status(409).body(errorResponse);
            }

            backupService.runBackup(source, destination);

            List<BackupTask> recentTasks = backupRepository.findBySourcePathAndDestinationPathOrderByIdDesc(source, destination);
            if (!recentTasks.isEmpty()) {
                taskIds.add(recentTasks.getFirst().getId());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Backup(s) iniciado(s) com sucesso");
        response.put("taskIds", taskIds);

        return ResponseEntity.ok(response);
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