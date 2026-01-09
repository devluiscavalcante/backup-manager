package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.dto.Progress;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@EnableAsync
public class BackupService {

    private final BackupManager backupManager;
    private final BackupRepository backupRepository;
    private final BackupCOntext backupCOntext;

    private final ProgressEmitter progressEmitter;
    private ExecutorService executor;

    public BackupService(
            BackupManager backupManager,
            BackupRepository backupRepository,
            BackupContext backupContext,
            ProgressEmitter progressEmitter
    ) {
        this.backupManager = backupManager;
        this.backupRepository = backupRepository;
        this.backupCOntext = backupContext;
        this.progressEmitter = progressEmitter;
    }

    @PostConstruct
    public void initi() {
        this.executor = Executors.newFixedThreadPool(4);
    }

    public void startMultipleBackups(List<String> sources, String destination) {
        validateDestination(destination);
        for (String source : sources) {
            executor.submit(() -> runBackup(source, destination));
        }
    }

    @Async
    public void runBackup(String sourcePath, String destinationPath) {
        BackupTask task = new BackupTask();
        task.setSourcePath(sourcePath);
        task.setDestinationPath(destinationPath);
        task.setStartedAt(LocalDateTime.now());
        task.setStatus(Status.EM_ANDAMENTO);

        try {
            File sourceFolder = backupManager.validateSource(sourcePath);
            BigDecimal sizeMB = backupManager.calculateFolderSizeMB(sourceFolder);

            Path source = sourceFolder.toPath();
            Path destination = Paths.get(destinationPath, sourceFolder.getName());

            BackupContext.setLastDestination(destination.toString());

            if (!Files.exists(destination)) Files.createDirectories(destination);

            progressEmitter.sendProgress(new Progress(
                    0,
                    "Iniciando...",
                    0,
                    (int) fileCount
            ));

            int warnings = copyDirectoryRecursively(source, destination);

            task.setFinishedAt(LocalDateTime.now());
            task.setFileCount(fileCount);
            task.setTotalSizeMB(sizeMB);
            task.setStatus(Status.CONCLUIDO);
            task.setErrorMessage(warnings > 0
                    ? "Concluído com alertas: " + warnings + " item(ns) ignorado(s). Consulte warnings.log no destino."
                    : null);

            progressEmitter.sendProgress(new Progress(
                    100,
                    "Backup concluído",
                    (int) fileCount,
                    (int) fileCount
            ));
        } catch (Exception e) {
            task.setStatus(Status.FALHA);
            task.setErrorMessage(e.getMessage());

            // opcional: notificar falha por SSE
            try {
                progressEmitter.sendProgress(new Progress(
                        0,
                        "Falha: " + e.getMessage(),
                        0,
                        0
                ));
            } catch (Exception ignored) {}
        }
    }

    task.setFinishedAt(LocalDateTime.now());
    backupRepository.save(task);

}
