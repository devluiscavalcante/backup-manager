package com.backup_manager.application.service;

import com.backup_manager.application.dto.Progress;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.infrastructure.logging.BackupContext;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
    private final BackupContext backupContext;
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
        this.backupContext = backupContext;
        this.progressEmitter = progressEmitter;
    }

    @PostConstruct
    public void init() {
        this.executor = Executors.newFixedThreadPool(4);
    }

    public void validateDestination(String destination) {
        backupManager.validateDestination(destination);
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
        backupRepository.save(task);

        try {
            File sourceFolder = backupManager.validateSource(sourcePath);
            BigDecimal sizeMB = backupManager.calculateFolderSizeMB(sourceFolder);
            long fileCount = backupManager.countFiles(sourceFolder);

            Path source = sourceFolder.toPath();
            Path destination = Paths.get(destinationPath, sourceFolder.getName());

            backupContext.setLastDestination(destination.toString());

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

            try {
                progressEmitter.sendProgress(new Progress(
                        0,
                        "Falha: " + e.getMessage(),
                        0,
                        0
                ));
            } catch (Exception ignored) {
            }
        }

        task.setFinishedAt(LocalDateTime.now());
        backupRepository.save(task);
    }

    @Async
    public void runBackup(String sourcePath, List<String> destinationPaths) {
        if (destinationPaths == null || destinationPaths.isEmpty()) {
            throw new IllegalArgumentException("Lista de destinos não pode estar vazia.");
        }

        for (String destinationPath : destinationPaths) {
            try {
                System.out.println("[BackupService] Iniciando backup para destino: " + destinationPath);
                runBackup(sourcePath, destinationPath);
            } catch (Exception e) {
                System.err.println("[BackupService] Falha ao copiar para destino '" + destinationPath + "': " + e.getMessage());
            }
        }
    }

    private int copyDirectoryRecursively(Path source, Path destination) {
        List<String> excludedFolders = List.of(
                "AppData", "Ambiente de Impressão", "Meus Vídeos",
                "Links", "Saved Games", "Searches", "Favorites",
                "MicrosoftEdgeBackups"
        );

        AtomicInteger warnings = new AtomicInteger(0);
        Path logFile = destination.resolve("warnings.log");

        try {

            if (!Files.exists(destination)) {
                Files.createDirectories(destination);
            }

            Files.walkFileTree(source, new SimpleFileVisitor<>() {

                int processed = 0;
                int total = 0;

                {
                    try {
                        total = (int) backupManager.countFiles(source.toFile());
                        if (total < 0) total = 0;
                    } catch (Exception ignored) {
                        total = 0;
                    }
                }

                private boolean shouldExclude(Path path, BasicFileAttributes attrs) {
                    try {
                        if (attrs.isOther() || Files.isSymbolicLink(path)) return true;
                        String p = path.toString();
                        for (String excluded : excludedFolders) {
                            if (p.contains(excluded)) return true;
                        }
                    } catch (Exception ignored) {
                    }
                    return false;
                }

                private void logWarning(String message, Path path) {
                    warnings.incrementAndGet();
                    String logEntry = String.format("[%s] %s: %s%n",
                            LocalDateTime.now(), message, path);
                    System.err.println(logEntry);
                    try {
                        Files.writeString(logFile, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException ignored) {
                    }
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldExclude(dir, attrs)) {
                        logWarning("Ignorado diretório simbólico/junction", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path targetDir = destination.resolve(source.relativize(dir));
                    try {
                        Files.createDirectories(targetDir);
                    } catch (AccessDeniedException ade) {
                        logWarning("Acesso negado ao diretório", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    } catch (IOException e) {
                        logWarning("Erro ao criar diretório destino", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (shouldExclude(file, attrs)) {
                        logWarning("Ignorado arquivo simbólico/junction", file);
                        return FileVisitResult.CONTINUE;
                    }
                    Path targetFile = destination.resolve(source.relativize(file));
                    try {
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

                        processed++;
                        int percent = 0;
                        if (total > 0) {
                            percent = (processed * 100) / total;
                        } else {
                            percent = 0;
                        }

                        try {
                            progressEmitter.sendProgress(new Progress(
                                    percent,
                                    file.toString(),
                                    processed,
                                    total
                            ));
                        } catch (Exception ignored) {
                        }

                    } catch (AccessDeniedException ade) {
                        logWarning("Acesso negado ao arquivo", file);
                    } catch (IOException e) {
                        logWarning("Erro ao copiar arquivo", file);
                    } catch (Exception e) {
                        logWarning("Erro inesperado ao copiar arquivo", file);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    logWarning("Falha ao visitar arquivo/pasta", file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {

            String msg = "Erro ao percorrer diretório: " + e.getMessage();
            try {
                Files.writeString(logFile, String.format("[%s] %s%n", LocalDateTime.now(), msg), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
            throw new RuntimeException(msg, e);
        }

        return warnings.get();
    }

    public List<BackupTask> getAllTasks() {
        return backupRepository.findAll();
    }
}

