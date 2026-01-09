package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.domain.model.BackupTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;
    private final ProgressEmitter progressEmitter;

    public BackupController(BackupService backupService, ProgressEmitter progressEmitter) {
        this.backupService;
        this.progressEmitter;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startBackup(@RequestBody BackupRequest request) {
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
            backupService.runBackup(source, destination);
        }

        return ResponseEntity.ok("Backup iniciado com sucesso");
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
                        task.getTotalSizeMB()
                );

                responseList.add(dto);
            }

            return ResponseEntity.ok(responseList);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao listar histórico: " + e.getMessage());
        }
    }
}