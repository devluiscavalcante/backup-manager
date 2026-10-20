package com.backup_manager.application.service;

import com.backup_manager.application.dto.Progress;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.exception.BackupInitializationException;
import com.backup_manager.domain.event.BackupCompletedEvent;
import com.backup_manager.domain.event.BackupFailedEvent;
import com.backup_manager.domain.event.BackupStartedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final int MAX_UPDATE_ATTEMPTS = 3;

    private final BackupManager backupManager;
    private final BackupRepository backupRepository;
    private final ProgressEmitter progressEmitter;
    private final BackupTaskManager taskManager;
    private final FileStorageOperations storageOps;
    private final ApplicationEventPublisher eventPublisher;
    private final PathSecurityService pathSecurityService;
    private final Executor backupDispatchExecutor;
    private final BackupService self;

    @Value("${backup.exclusions:AppData,Temp,node_modules,.git,$RECYCLE.BIN}")
    private List<String> excludedFolders;

    public BackupService(
            BackupManager backupManager,
            BackupRepository backupRepository,
            ProgressEmitter progressEmitter,
            BackupTaskManager taskManager,
            FileStorageOperations storageOps,
            ApplicationEventPublisher eventPublisher,
            PathSecurityService pathSecurityService,
            @Qualifier("backupDispatchExecutor") Executor backupDispatchExecutor,
            @Lazy BackupService self
    ) {
        this.backupManager = backupManager;
        this.backupRepository = backupRepository;
        this.progressEmitter = progressEmitter;
        this.taskManager = taskManager;
        this.storageOps = storageOps;
        this.eventPublisher = eventPublisher;
        this.pathSecurityService = pathSecurityService;
        this.backupDispatchExecutor = backupDispatchExecutor;
        this.self = self;
    }

    public void validateDestination(String destination) {
        backupManager.validateDestination(destination);
    }

    public void startMultipleBackups(List<String> sources, String destination) {
        validateDestination(destination);
        for (String source : sources) {
            backupDispatchExecutor.execute(() -> {
                try {
                    runBackup(source, destination);
                } catch (RuntimeException e) {
                    logger.error("Falha ao despachar backup [{} -> {}]: {}",
                            source, destination, e.getMessage(), e);
                }
            });
        }
    }

    public Long runBackup(String sourcePath, String destinationPath) {
        try {
            Path validatedSource = validateSafePath(sourcePath);
            Path validatedDestination = validateSafePath(destinationPath);
            pathSecurityService.ensureNotOverlapping(validatedSource, validatedDestination, "backup");
            validatePathAndDriveSpace(sourcePath, destinationPath);

            BackupTask task = createInitialTask(sourcePath, destinationPath);
            task = backupRepository.save(task);

            final Long taskId = task.getId();
            logger.info("Backup criado com ID={}, iniciando processamento assincrono", taskId);

            // A chamada via proxy garante que a anotacao @Async seja aplicada de fato.
            try {
                self.processBackupAsync(task);
            } catch (TaskRejectedException rejected) {
                // Sem isso a tarefa ja salva ficaria EM_ANDAMENTO ate o proximo restart.
                markRejected(task);
                throw new IllegalStateException(
                        "Fila de backups cheia. Aguarde a conclusao de backups em andamento e tente novamente.",
                        rejected
                );
            }
            return taskId;

        } catch (SecurityException se) {
            logger.error("BLOQUEIO DE SEGURANCA: {}", se.getMessage());
            progressEmitter.sendError("Erro de Seguranca: " + se.getMessage());
            throw se;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            logger.error("FALHA NA VALIDACAO PREVIA: {}", ex.getMessage());
            progressEmitter.sendError("Falha ao iniciar: " + ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            logger.error("FALHA OPERACIONAL NA PREPARACAO: {}", ex.getMessage(), ex);
            progressEmitter.sendError("Falha ao preparar o backup.");
            throw new BackupInitializationException("Falha ao preparar o backup.", ex);
        }
    }

    public List<Long> runBackup(String sourcePath, List<String> destinationPaths) {
        if (destinationPaths == null || destinationPaths.isEmpty()) {
            throw new IllegalArgumentException("Lista de destinos nao pode estar vazia.");
        }

        List<Long> taskIds = new ArrayList<>();
        for (String destinationPath : destinationPaths) {
            taskIds.add(runBackup(sourcePath, destinationPath));
        }
        return taskIds;
    }

    @Async("backupTaskExecutor")
    public void processBackupAsync(BackupTask task) {
        try {
            logger.info("Iniciando processamento assincrono: ID={}, Status={}", task.getId(), task.getStatus());

            BackupTask activeTask = taskManager.activateQueuedTask(task.getId(), task);
            if (isCancelled(activeTask)) {
                logger.info("Backup {} cancelado enquanto aguardava na fila, execucao ignorada", task.getId());
                taskManager.unregisterTask(task.getId());
                return;
            }

            progressEmitter.sendControlEvent("start", task.getId(), "EM_ANDAMENTO");
            eventPublisher.publishEvent(new BackupStartedEvent(task, false));

            try {
                File sourceFolder = backupManager.validateSource(task.getSourcePath());
                BigDecimal sizeMB = backupManager.calculateFolderSizeMB(sourceFolder);
                long fileCount = backupManager.countFiles(sourceFolder);
                Path source = sourceFolder.toPath();
                Path destination = Paths.get(task.getDestinationPath());

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

                if (finalizeTask(task.getId(), fileCount, sizeMB, warnings).isEmpty()) {
                    logger.info("Backup {} cancelado, pulando finalizacao", task.getId());
                }

            } catch (Exception e) {
                if (handleBackupFailure(task.getId(), e).isEmpty()) {
                    logger.info("Backup {} cancelado (exception capturada), pulando tratamento de falha", task.getId());
                }
            } finally {
                updateUnlessCancelled(task.getId(), finalTask -> {
                    if (finalTask.getFinishedAt() == null) {
                        finalTask.setFinishedAt(LocalDateTime.now());
                    }
                });
                taskManager.unregisterTask(task.getId());
            }

        } catch (Exception e) {
            logger.error("Erro critico no processamento assincrono da task {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    private static boolean isCancelled(BackupTask task) {
        return task != null && (task.isCancelled() || task.getStatus() == Status.CANCELADO);
    }

    private int executeStorageOperation(Path source, Path destination, Long taskId, int totalFiles) throws IOException {
        AtomicInteger processed = new AtomicInteger(0);
        Path logFile = destination.resolve(FileStorageOperations.WARNINGS_LOG);
        // O arquivo acumula execucoes: o cabecalho separa os alertas de cada uma.
        appendLog(logFile, String.format("%n=== Backup #%d iniciado em %s ===%n", taskId, LocalDateTime.now()));

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

    public Path validateSafePath(String path) {
        return pathSecurityService.validateManagedPath(path, "backup");
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

    private Optional<BackupTask> finalizeTask(Long taskId, long fileCount, BigDecimal sizeMB, int warnings) {
        Optional<BackupTask> finalized = updateUnlessCancelled(taskId, task -> {
            task.setFinishedAt(LocalDateTime.now());
            task.setFileCount(fileCount);
            task.setTotalSizeMB(sizeMB);
            task.setStatus(Status.CONCLUIDO);
            task.setErrorMessage(warnings > 0
                    ? "Concluido com " + warnings + " alertas. Verifique " + FileStorageOperations.WARNINGS_LOG + "."
                    : null);
        });
        if (finalized.isEmpty()) {
            return finalized;
        }

        BackupTask persistedTask = finalized.get();
        long durationSeconds = calculateDuration(persistedTask);

        progressEmitter.sendControlEvent("complete", persistedTask.getId(), "CONCLUIDO");
        progressEmitter.sendProgress(new Progress(
                100,
                "Concluido",
                (int) fileCount,
                (int) fileCount,
                persistedTask.getId().toString()
        ));

        eventPublisher.publishEvent(new BackupCompletedEvent(persistedTask, durationSeconds));
        return finalized;
    }

    private Optional<BackupTask> handleBackupFailure(Long taskId, Exception e) {
        logger.error("Falha no backup {}: {}", taskId, e.getMessage());

        Optional<BackupTask> failed = updateUnlessCancelled(taskId, task -> {
            task.setStatus(Status.FALHA);
            task.setErrorMessage(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
        });
        if (failed.isEmpty()) {
            return failed;
        }

        BackupTask persistedTask = failed.get();
        progressEmitter.sendControlEvent("error", persistedTask.getId(), "FALHA");
        progressEmitter.sendError("Falha: " + e.getMessage());

        eventPublisher.publishEvent(new BackupFailedEvent(persistedTask, e.getMessage()));
        return failed;
    }

    /**
     * Grava sobre a versao atual do banco, nunca sobre um snapshot antigo. Se um cancelamento chegar
     * entre a leitura e o save, o @Version recusa a gravacao e a releitura encontra a tarefa cancelada,
     * que entao e preservada. Vazio significa que a tarefa foi cancelada (ou removida).
     */
    Optional<BackupTask> updateUnlessCancelled(Long taskId, Consumer<BackupTask> change) {
        for (int attempt = 1; attempt <= MAX_UPDATE_ATTEMPTS; attempt++) {
            BackupTask current = backupRepository.findById(taskId).orElse(null);
            if (current == null || isCancelled(current) || isCancelled(taskManager.getTask(taskId))) {
                return Optional.empty();
            }

            change.accept(current);
            try {
                return Optional.of(backupRepository.save(current));
            } catch (OptimisticLockingFailureException conflict) {
                logger.info("Backup {} alterado concorrentemente, relendo (tentativa {}/{})",
                        taskId, attempt, MAX_UPDATE_ATTEMPTS);
            }
        }

        logger.warn("Backup {} nao atualizado: conflito de concorrencia persistente", taskId);
        return Optional.empty();
    }

    private void markRejected(BackupTask task) {
        logger.error("Fila de backups cheia: tarefa {} recusada", task.getId());
        updateUnlessCancelled(task.getId(), rejected -> {
            rejected.setStatus(Status.FALHA);
            rejected.setErrorMessage("Fila de backups cheia: tarefa nao iniciada");
            rejected.setFinishedAt(LocalDateTime.now());
        });
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
        // Busca diretamente no banco para evitar carregar todas as tarefas em memoria.
        return backupRepository.findFirstBySourcePathAndDestinationPathAndStatusInOrderByIdDesc(
                sourcePath,
                destinationPath,
                List.of(Status.EM_ANDAMENTO, Status.PAUSADO)
        );
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
            return ChronoUnit.SECONDS.between(task.getStartedAt(), task.getFinishedAt());
        }
        return 0;
    }

    private void logDetail(Path logFile, String message, Path path) {
        appendLog(logFile, String.format("[%s] [%s] %s: %s%n", LocalDateTime.now(), "WARNING", message, path));
    }

    private void appendLog(Path logFile, String entry) {
        try {
            Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
