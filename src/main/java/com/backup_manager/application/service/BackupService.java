package com.backup_manager.application.service;

import com.backup_manager.application.dto.Progress;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.event.BackupCompletedEvent;
import com.backup_manager.domain.event.BackupFailedEvent;
import com.backup_manager.domain.event.BackupStartedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.logging.BackupContext;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    private final BackupManager backupManager;
    private final BackupRepository backupRepository;
    private final BackupContext backupContext;
    private final ProgressEmitter progressEmitter;
    private final BackupTaskManager taskManager;
    private final FileStorageOperations storageOps;
    private final ApplicationEventPublisher eventPublisher;
    private final PathSecurityService pathSecurityService;
    private final BackupService self;

    @Value("${backup.excluded-folders:AppData,Temp,node_modules}")
    private List<String> excludedFolders;

    private ExecutorService executor;

    public BackupService(
            BackupManager backupManager,
            BackupRepository backupRepository,
            BackupContext backupContext,
            ProgressEmitter progressEmitter,
            BackupTaskManager taskManager,
            FileStorageOperations storageOps,
            ApplicationEventPublisher eventPublisher,
            PathSecurityService pathSecurityService,
            @Lazy BackupService self
    ) {
        this.backupManager = backupManager;
        this.backupRepository = backupRepository;
        this.backupContext = backupContext;
        this.progressEmitter = progressEmitter;
        this.taskManager = taskManager;
        this.storageOps = storageOps;
        this.eventPublisher = eventPublisher;
        this.pathSecurityService = pathSecurityService;
        this.self = self;
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

    public Long runBackup(String sourcePath, String destinationPath) {
        try {
            validateSafePath(sourcePath);
            validateSafePath(destinationPath);
            validatePathAndDriveSpace(sourcePath, destinationPath);

            BackupTask task = createInitialTask(sourcePath, destinationPath);
            task = backupRepository.save(task);

            final Long taskId = task.getId();
            logger.info("Backup criado com ID={}, iniciando processamento assincrono", taskId);

            // A chamada via proxy garante que a anotacao @Async seja aplicada de fato.
            self.processBackupAsync(task);
            return taskId;

        } catch (SecurityException se) {
            logger.error("BLOQUEIO DE SEGURANCA: {}", se.getMessage());
            progressEmitter.sendError("Erro de Seguranca: " + se.getMessage());
            return null;
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            logger.error("FALHA NA VALIDACAO PREVIA: {}", ex.getMessage());
            progressEmitter.sendError("Falha ao iniciar: " + ex.getMessage());
            return null;
        }
    }

    public List<Long> runBackup(String sourcePath, List<String> destinationPaths) {
        if (destinationPaths == null || destinationPaths.isEmpty()) {
            throw new IllegalArgumentException("Lista de destinos nao pode estar vazia.");
        }

        List<Long> taskIds = new ArrayList<>();
        for (String destinationPath : destinationPaths) {
            Long taskId = runBackup(sourcePath, destinationPath);
            if (taskId != null) {
                taskIds.add(taskId);
            }
        }
        return taskIds;
    }

    @Async("backupTaskExecutor")
    public void processBackupAsync(BackupTask task) {
        try {
            logger.info("Iniciando processamento assincrono: ID={}, Status={}", task.getId(), task.getStatus());

            taskManager.registerTask(task.getId(), task);
            progressEmitter.sendControlEvent("start", task.getId(), "EM_ANDAMENTO");
            eventPublisher.publishEvent(new BackupStartedEvent(task, false));

            try {
                File sourceFolder = backupManager.validateSource(task.getSourcePath());
                BigDecimal sizeMB = backupManager.calculateFolderSizeMB(sourceFolder);
                long fileCount = backupManager.countFiles(sourceFolder);
                Path source = sourceFolder.toPath();
                Path destination = Paths.get(task.getDestinationPath());

                backupContext.setLastDestination(destination.toString());

                if (!Files.exists(destination)) {
                    Files.createDirectories(destination);
                }

                progressEmitter.sendProgress(new Progress(
                        0,
                        "Iniciando...",
                        0,
                        (int) fileCount,
                        task.getId().toString()
                ));

                int warnings = executeStorageOperation(source, destination, task.getId(), (int) fileCount);

                BackupTask memoryTask = taskManager.getTask(task.getId());
                BackupTask dbTask = backupRepository.findById(task.getId()).orElse(null);

                logger.info(
                        "PRE-FINALIZE CHECK - ID={}: memoryTask exists={}, memory.isCancelled={}, memory.status={}",
                        task.getId(),
                        memoryTask != null,
                        memoryTask != null ? memoryTask.isCancelled() : "N/A",
                        memoryTask != null ? memoryTask.getStatus() : "N/A"
                );

                logger.info(
                        "PRE-FINALIZE CHECK - ID={}: dbTask exists={}, db.isCancelled={}, db.status={}",
                        task.getId(),
                        dbTask != null,
                        dbTask != null ? dbTask.isCancelled() : "N/A",
                        dbTask != null ? dbTask.getStatus() : "N/A"
                );

                boolean wasCancelled = false;
                String cancelSource = "";

                if (memoryTask != null && memoryTask.isCancelled()) {
                    wasCancelled = true;
                    cancelSource = "memory.isCancelled";
                }

                if (memoryTask != null && memoryTask.getStatus() == Status.CANCELADO) {
                    wasCancelled = true;
                    cancelSource = cancelSource.isEmpty() ? "memory.status" : cancelSource + " + memory.status";
                }

                if (dbTask != null && dbTask.isCancelled()) {
                    wasCancelled = true;
                    cancelSource = cancelSource.isEmpty() ? "db.isCancelled" : cancelSource + " + db.isCancelled";
                }

                if (dbTask != null && dbTask.getStatus() == Status.CANCELADO) {
                    wasCancelled = true;
                    cancelSource = cancelSource.isEmpty() ? "db.status" : cancelSource + " + db.status";
                }

                if (wasCancelled) {
                    logger.info("DECISAO: Backup {} CANCELADO (fonte: {}), pulando finalizacao", task.getId(), cancelSource);
                } else {
                    logger.info("DECISAO: Backup {} NAO CANCELADO, prosseguindo com finalizacao", task.getId());
                    finalizeTask(dbTask != null ? dbTask : task, fileCount, sizeMB, warnings);
                }

            } catch (Exception e) {
                BackupTask memoryTask = taskManager.getTask(task.getId());
                BackupTask dbTask = backupRepository.findById(task.getId()).orElse(null);

                boolean wasCancelled = false;
                if (memoryTask != null && (memoryTask.isCancelled() || memoryTask.getStatus() == Status.CANCELADO)) {
                    wasCancelled = true;
                }
                if (dbTask != null && (dbTask.isCancelled() || dbTask.getStatus() == Status.CANCELADO)) {
                    wasCancelled = true;
                }

                if (wasCancelled) {
                    logger.info("Backup {} cancelado (exception capturada), pulando tratamento de falha", task.getId());
                } else {
                    handleBackupFailure(dbTask != null ? dbTask : task, e);
                }
            } finally {
                BackupTask memoryTask = taskManager.getTask(task.getId());

                if (memoryTask != null &&
                        (memoryTask.isCancelled() || memoryTask.getStatus() == Status.CANCELADO)) {
                    logger.info("Finally: Backup {} cancelado, nao sobrescreve estado", task.getId());
                    taskManager.unregisterTask(task.getId());
                    return;
                }

                BackupTask finalTask = backupRepository.findById(task.getId()).orElse(task);
                if (finalTask.getFinishedAt() == null) {
                    finalTask.setFinishedAt(LocalDateTime.now());
                    backupRepository.save(finalTask);
                }

                taskManager.unregisterTask(task.getId());
            }

        } catch (Exception e) {
            logger.error("Erro critico no processamento assincrono da task {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    private int executeStorageOperation(Path source, Path destination, Long taskId, int totalFiles) throws IOException {
        AtomicInteger processed = new AtomicInteger(0);
        Path logFile = destination.resolve("warnings.log");

        return storageOps.copyDirectoryIncremental(
                source,
                destination,
                excludedFolders,
                new FileStorageOperations.BackupProgressCallback() {
                    @Override
                    public void onFileProcessed(Path file, BasicFileAttributes attrs) {
                        int current = processed.incrementAndGet();
                        updateProgress(file, current, totalFiles, taskId);
                    }

                    @Override
                    public void onWarning(String message, Path path) {
                        logDetail(logFile, message, path);
                    }

                    @Override
                    public boolean shouldContinue() {
                        BackupTask task = taskManager.getTask(taskId);
                        if (task == null || task.isCancelled()) {
                            return false;
                        }

                        handlePause(task, taskId, processed.get(), totalFiles);
                        return !task.isCancelled();
                    }
                }
        );
    }

    public void validatePathAndDriveSpace(String sourcePath, String destPath) throws IOException {
        Path src = Paths.get(sourcePath);
        Path dest = Paths.get(destPath);

        if (!Files.exists(src)) {
            throw new IllegalArgumentException("Origem nao encontrada: " + sourcePath);
        }

        try (var stream = Files.list(src)) {
            if (stream.findAny().isEmpty()) {
                throw new IllegalArgumentException("A pasta de origem esta vazia: " + sourcePath);
            }
        }

        File destRoot = dest.getRoot().toFile();
        if (!destRoot.exists()) {
            throw new IllegalStateException("O disco de destino " + dest.getRoot() + " nao esta acessivel.");
        }

        long requiredSpace = backupManager.calculateFolderSizeMB(src.toFile()).longValue() * 1024 * 1024;
        long availableSpace = destRoot.getUsableSpace();

        if (requiredSpace > availableSpace) {
            String error = String.format(
                    "Espaco insuficiente no disco %s. Necessario: %d MB, Disponivel: %d MB",
                    dest.getRoot(),
                    requiredSpace / (1024 * 1024),
                    availableSpace / (1024 * 1024)
            );
            throw new IOException(error);
        }
    }

    public void validateSafePath(String path) {
        pathSecurityService.validateManagedPath(path, "backup");
    }

    private void handlePause(BackupTask task, Long taskId, int processed, int total) {
        int pauseCheckCount = 0;
        while (task.isPaused() && !task.isCancelled()) {
            if (pauseCheckCount == 0) {
                progressEmitter.sendProgress(new Progress(0, "Backup pausado...", 0, 0, taskId.toString()));
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            task = taskManager.getTask(taskId);
            pauseCheckCount++;
        }

        if (pauseCheckCount > 0 && !task.isCancelled()) {
            updateProgress(null, processed, total, taskId);
        }
    }

    private BackupTask createInitialTask(String source, String dest) {
        BackupTask task = new BackupTask();
        task.setSourcePath(source);
        task.setDestinationPath(dest);
        task.setStartedAt(LocalDateTime.now());
        task.setStatus(Status.EM_ANDAMENTO);
        return task;
    }

    private void finalizeTask(BackupTask task, long fileCount, BigDecimal sizeMB, int warnings) {
        task.setFinishedAt(LocalDateTime.now());
        task.setFileCount(fileCount);
        task.setTotalSizeMB(sizeMB);
        task.setStatus(Status.CONCLUIDO);
        task.setErrorMessage(warnings > 0
                ? "Concluido com " + warnings + " alertas. Verifique warnings.log."
                : null);

        long durationSeconds = calculateDuration(task);
        BackupTask persistedTask = backupRepository.save(task);

        progressEmitter.sendControlEvent("complete", persistedTask.getId(), "CONCLUIDO");
        progressEmitter.sendProgress(new Progress(
                100,
                "Concluido",
                (int) fileCount,
                (int) fileCount,
                persistedTask.getId().toString()
        ));

        eventPublisher.publishEvent(new BackupCompletedEvent(persistedTask, durationSeconds));
    }

    private void handleBackupFailure(BackupTask task, Exception e) {
        logger.error("Falha no backup {}: {}", task.getId(), e.getMessage());

        task.setStatus(Status.FALHA);
        task.setErrorMessage(e.getMessage());
        task.setFinishedAt(LocalDateTime.now());

        BackupTask persistedTask = backupRepository.save(task);
        progressEmitter.sendControlEvent("error", persistedTask.getId(), "FALHA");
        progressEmitter.sendError("Falha: " + e.getMessage());

        eventPublisher.publishEvent(new BackupFailedEvent(persistedTask, e.getMessage()));
    }

    public List<BackupTask> getAllTasks() {
        return backupRepository.findAll();
    }

    public boolean pauseBackup(Long id) {
        return taskManager.pauseTask(id);
    }

    public boolean resumeBackup(Long id) {
        return taskManager.resumeTask(id);
    }

    public boolean cancelBackup(Long id) {
        return taskManager.cancelTask(id);
    }

    public Optional<BackupTask> getActiveTask(String sourcePath, String destinationPath) {
        return backupRepository.findAll().stream()
                .filter(task -> task.getSourcePath().equals(sourcePath)
                        && task.getDestinationPath().equals(destinationPath)
                        && (task.getStatus() == Status.EM_ANDAMENTO || task.getStatus() == Status.PAUSADO))
                .findFirst();
    }

    private void updateProgress(Path file, int processed, int total, Long taskId) {
        if (processed == 1 || processed >= total || processed % 50 == 0) {
            int percent = total > 0 ? (processed * 100) / total : 0;
            String fileName = file != null ? file.getFileName().toString() : "Processando...";
            try {
                progressEmitter.sendProgress(new Progress(percent, fileName, processed, total, taskId.toString()));
            } catch (Exception ignored) {
            }
        }
    }

    private long calculateDuration(BackupTask task) {
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            return java.time.temporal.ChronoUnit.SECONDS.between(task.getStartedAt(), task.getFinishedAt());
        }
        return 0;
    }

    private void logDetail(Path logFile, String message, Path path) {
        String entry = String.format("[%s] [%s] %s: %s%n", LocalDateTime.now(), "WARNING", message, path);
        try {
            Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
