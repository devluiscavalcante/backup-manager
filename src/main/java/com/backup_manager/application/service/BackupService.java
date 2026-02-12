package com.backup_manager.application.service;

import com.backup_manager.application.dto.Progress;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.logging.BackupContext;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;
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
    private final BackupTaskManager taskManager;

    private ExecutorService executor;

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    public BackupService(
            BackupManager backupManager,
            BackupRepository backupRepository,
            BackupContext backupContext,
            ProgressEmitter progressEmitter,
            BackupTaskManager taskManager
    ) {
        this.backupManager = backupManager;
        this.backupRepository = backupRepository;
        this.backupContext = backupContext;
        this.progressEmitter = progressEmitter;
        this.taskManager = taskManager;
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

        task = backupRepository.save(task);
        logger.info("Tarefa salva no banco: ID={}, Status={}", task.getId(), task.getStatus());

        taskManager.registerTask(task.getId(), task);

        progressEmitter.sendControlEvent("start", task.getId(), "EM_ANDAMENTO");

        try {
            File sourceFolder = backupManager.validateSource(sourcePath);
            BigDecimal sizeMB = backupManager.calculateFolderSizeMB(sourceFolder);
            long fileCount = backupManager.countFiles(sourceFolder);

            Path source = sourceFolder.toPath();
            Path destination = Paths.get(destinationPath);

            backupContext.setLastDestination(destination.toString());

            if (!Files.exists(destination)) Files.createDirectories(destination);

            progressEmitter.sendProgress(new Progress(
                    0,
                    "Iniciando...",
                    0,
                    (int) fileCount,
                    task.getId().toString()
            ));

            int warnings = copyDirectoryRecursively(source, destination, task.getId());

            if (task.isCancelled()) {
                task.setStatus(Status.CANCELADO);
                task.setErrorMessage("Backup cancelado pelo usuário");
                backupRepository.save(task);

                progressEmitter.sendControlEvent("cancel", task.getId(), "CANCELADO");
                progressEmitter.sendProgress(new Progress(
                        0,
                        "Backup cancelado",
                        0,
                        0,
                        task.getId().toString()
                ));
            } else {
                task.setFinishedAt(LocalDateTime.now());
                task.setFileCount(fileCount);
                task.setTotalSizeMB(sizeMB);
                task.setStatus(Status.CONCLUIDO);
                task.setErrorMessage(warnings > 0
                        ? "Concluído com alertas: " + warnings + " item(ns) ignorado(s). Consulte warnings.log no destino."
                        : null);
                backupRepository.save(task);

                progressEmitter.sendControlEvent("complete", task.getId(), "CONCLUIDO");
                progressEmitter.sendProgress(new Progress(
                        100,
                        "Backup concluído",
                        (int) fileCount,
                        (int) fileCount,
                        task.getId().toString()
                ));
            }

        } catch (Exception e) {
            task.setStatus(Status.FALHA);
            task.setErrorMessage(e.getMessage());
            backupRepository.save(task);

            progressEmitter.sendControlEvent("error", task.getId(), "FALHA");
            progressEmitter.sendError("Falha no backup: " + e.getMessage());

            try {
                progressEmitter.sendProgress(new Progress(
                        0,
                        "Falha: " + e.getMessage(),
                        0,
                        0,
                        task.getId().toString()
                ));
            } catch (Exception ignored) {
            }
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            backupRepository.save(task);
            taskManager.unregisterTask(task.getId());
        }
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


    private int copyDirectoryRecursively(Path source, Path destination, Long taskId) {
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
                int total;

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

                private FileVisitResult checkPauseAndCancel() {
                    BackupTask task = taskManager.getTask(taskId);
                    if (task == null) {
                        logger.warn("Tarefa {} não encontrada no gerenciador", taskId);
                        return FileVisitResult.TERMINATE;
                    }

                    if (task.isCancelled()) {
                        logger.info("Backup {} cancelado pelo usuário", taskId);
                        return FileVisitResult.TERMINATE;
                    }

                    int pauseCheckCount = 0;
                    while (task.isPaused() && !task.isCancelled()) {
                        if (pauseCheckCount == 0) {
                            try {
                                progressEmitter.sendProgress(new Progress(
                                        0,
                                        "Backup pausado...",
                                        0,
                                        0,
                                        taskId.toString()
                                ));
                            } catch (Exception e) {
                                logger.warn("Erro ao enviar progresso de pausa: {}", e.getMessage());
                            }
                        }

                        pauseCheckCount++;

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return FileVisitResult.TERMINATE;
                        }

                        task = taskManager.getTask(taskId);
                        if (task == null) return FileVisitResult.TERMINATE;

                        if (pauseCheckCount % 10 == 0) {
                            logger.debug("Backup {} ainda pausado (verificação #{})", taskId, pauseCheckCount);
                        }
                    }

                    if (pauseCheckCount > 0) {
                        logger.info("Backup {} retomado após pausa", taskId);
                        updateProgress(null, processed, total, taskId); // Envia update de retomada
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    FileVisitResult result = checkPauseAndCancel();
                    if (result != FileVisitResult.CONTINUE) return result;

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
                    FileVisitResult result = checkPauseAndCancel();
                    if (result != FileVisitResult.CONTINUE) return result;

                    if (shouldExclude(file, attrs)) {
                        logWarning("Ignorado arquivo simbólico/junction", file);
                        return FileVisitResult.CONTINUE;
                    }

                    Path targetFile = destination.resolve(source.relativize(file));
                    try {

                        boolean skipCopy = false;
                        if (Files.exists(targetFile)) {
                            BasicFileAttributes targetAttrs = Files.readAttributes(targetFile, BasicFileAttributes.class);

                            boolean isSameSize = attrs.size() == targetAttrs.size();
                            boolean isNotModified = attrs.lastModifiedTime().toMillis() <= targetAttrs.lastModifiedTime().toMillis();

                            if (isSameSize && isNotModified) {
                                skipCopy = true;
                            }
                        }

                        if (!skipCopy) {
                            Files.createDirectories(targetFile.getParent());
                            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        }

                        processed++;
                        updateProgress(file, processed, total, taskId);

                    } catch (AccessDeniedException ade) {
                        logWarning("Acesso negado ao arquivo", file);
                    } catch (IOException e) {
                        logWarning("Erro ao processar arquivo (pode estar em uso): " + file.getFileName(), file);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    logWarning("Falha ao visitar arquivo/pasta", file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) logWarning("Erro ao visitar diretório", dir);
                    return checkPauseAndCancel();
                }
            });
        } catch (IOException e) {
            String msg = "Erro ao percorrer diretório: " + e.getMessage();
            try {
                Files.writeString(logFile, String.format("[%s] %s%n", LocalDateTime.now(), msg),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {}
            throw new RuntimeException(msg, e);
        }

        return warnings.get();
    }

    public List<BackupTask> getAllTasks() {
        return backupRepository.findAll();
    }

    public boolean pauseBackup(Long taskId) {
        return taskManager.pauseTask(taskId);
    }

    public boolean resumeBackup(Long taskId) {
        return taskManager.resumeTask(taskId);
    }

    public boolean cancelBackup(Long taskId) {
        return taskManager.cancelTask(taskId);
    }

    public Optional<BackupTask> getActiveTask(String sourcePath, String destinationPath) {
        List<BackupTask> tasks = backupRepository.findAll();
        return tasks.stream()
                .filter(t -> t.getSourcePath().equals(sourcePath) &&
                        t.getDestinationPath().equals(destinationPath) &&
                        (t.getStatus() == Status.EM_ANDAMENTO ||
                                t.getStatus() == Status.PAUSADO))
                .findFirst();
    }

    private void updateProgress(Path file, int processed, int total, Long taskId) {

        if (processed == 1 || processed >= total || processed % 50 == 0) {
            int percent = (total > 0) ? (processed * 100) / total : 0;
            String fileName = (file != null) ? file.getFileName().toString() : "Retomando...";

            try {
                progressEmitter.sendProgress(new Progress(
                        percent,
                        fileName,
                        processed,
                        total,
                        taskId.toString()
                ));
            } catch (Exception ignored) {}
        }
    }
}