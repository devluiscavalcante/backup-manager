package com.backup_manager.application.service;

import com.backup_manager.application.dto.FileNodeDTO;
import com.backup_manager.application.dto.FileTreeDTO;
import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.domain.event.RestoreCompletedEvent;
import com.backup_manager.domain.event.RestoreFailedEvent;
import com.backup_manager.domain.event.RestoreStartedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.domain.model.RestoreType;
import com.backup_manager.domain.service.RestoreTaskManager;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import com.backup_manager.infrastructure.storage.FileRestoreOperations;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class RestoreService {

    private static final Logger logger = LoggerFactory.getLogger(RestoreService.class);

    private final BackupRepository backupRepository;
    private final RestoreRepository restoreRepository;
    private final RestoreTaskManager taskManager;
    private final FileRestoreOperations restoreOps;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PathSecurityService pathSecurityService;
    private final RestoreService self;

    public RestoreService(BackupRepository backupRepository,
                          RestoreRepository restoreRepository,
                          RestoreTaskManager taskManager,
                          FileRestoreOperations restoreOps,
                          ApplicationEventPublisher eventPublisher,
                          PathSecurityService pathSecurityService,
                          @Lazy RestoreService self) {
        this.backupRepository = backupRepository;
        this.restoreRepository = restoreRepository;
        this.taskManager = taskManager;
        this.restoreOps = restoreOps;
        this.eventPublisher = eventPublisher;
        this.pathSecurityService = pathSecurityService;
        this.objectMapper = new ObjectMapper();
        this.self = self;
    }

    public FileTreeDTO previewFiles(Long backupId) throws IOException {
        logger.info("Gerando preview de arquivos para backup ID={}", backupId);

        BackupTask backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup não encontrado: " + backupId));

        Path backupPath = Paths.get(backup.getDestinationPath());

        if (!Files.exists(backupPath)) {
            throw new IllegalStateException("Backup não encontrado no disco: " + backupPath);
        }

        // Materializa a listagem uma vez dentro do try-with-resources para evitar handles abertos.
        List<Path> backupEntries;
        try (Stream<Path> stream = Files.list(backupPath)) {
            backupEntries = stream.toList();
        }

        if (backupEntries.isEmpty()) {
            throw new IllegalStateException("Backup vazio: " + backupPath);
        }

        List<FileNodeDTO> files = new ArrayList<>();
        long[] totalFiles = {0};
        long[] totalSize = {0};

        backupEntries.forEach(path -> {
            try {
                FileNodeDTO node = buildFileNode(path, backupPath);
                files.add(node);

                if ("directory".equals(node.getType())) {
                    totalFiles[0] += node.getFileCount();
                } else {
                    totalFiles[0]++;
                }

                totalSize[0] += node.getSizeMB().multiply(BigDecimal.valueOf(1024 * 1024)).longValue();
            } catch (IOException e) {
                logger.warn("Erro ao processar arquivo {}: {}", path, e.getMessage());
            }
        });

        BigDecimal totalSizeMB = BigDecimal.valueOf(totalSize[0])
                .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP);

        FileTreeDTO tree = new FileTreeDTO();
        tree.setBackupId(backupId);
        tree.setSourcePath(backup.getSourcePath());
        tree.setBackupPath(backup.getDestinationPath());
        tree.setBackupDate(backup.getFinishedAt());
        tree.setTotalFiles(totalFiles[0]);
        tree.setTotalSizeMB(totalSizeMB);
        tree.setFiles(files);

        logger.info("Preview gerado: {} arquivos, {} MB", totalFiles[0], totalSizeMB);
        return tree;
    }

    private FileNodeDTO buildFileNode(Path path, Path root) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        Path relativePath = root.relativize(path);

        FileNodeDTO node = new FileNodeDTO();
        node.setName(path.getFileName().toString());
        node.setRelativePath(relativePath.toString());
        node.setLastModified(LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(),
                java.time.ZoneId.systemDefault()
        ));

        if (Files.isDirectory(path)) {
            node.setType("directory");

            long[] count = {0};
            long[] size = {0};

            try (Stream<Path> walk = Files.walk(path)) {
                walk.forEach(p -> {
                    try {
                        if (Files.isRegularFile(p)) {
                            count[0]++;
                            size[0] += Files.size(p);
                        }
                    } catch (IOException e) {
                        logger.debug("Erro ao processar {}: {}", p, e.getMessage());
                    }
                });
            }

            node.setFileCount(count[0]);
            node.setSizeMB(BigDecimal.valueOf(size[0])
                    .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP));
            node.setChildren(null); // Não carregar subárvore completa para performance
        } else {
            node.setType("file");
            node.setSizeMB(BigDecimal.valueOf(attrs.size())
                    .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP));
            node.setFileCount(null);
            node.setChildren(null);
        }

        return node;
    }

    public Long startFullRestore(Long backupId, RestoreRequest request) throws IOException {
        logger.info("Iniciando restauração completa: backupId={}, target={}",
                backupId, request.getTargetPath());

        BackupTask backup = validateBackup(backupId);
        // Reaproveita a allowlist compartilhada para impedir restauracoes fora das raizes aprovadas.
        pathSecurityService.validateWritableManagedPath(request.getTargetPath(), "restauracao");
        validateRestorePath(request.getTargetPath());

        RestoreTask task = createRestoreTask(backup, request.getTargetPath(),
                RestoreType.FULL, null);
        task = restoreRepository.save(task);

        final Long taskId = task.getId();
        // Usa o proxy Spring para que @Async seja realmente aplicado.
        self.processRestoreAsync(task, request.isOverwriteExisting(), null);

        return taskId;
    }

    public Long startSelectiveRestore(Long backupId, SelectiveRestoreRequest request) throws IOException {
        logger.info("Iniciando restauração seletiva: backupId={}, target={}, selectedFiles={}",
                backupId, request.getTargetPath(),
                request.getSelectedFiles() != null ? request.getSelectedFiles().size() : 0);

        BackupTask backup = validateBackup(backupId);
        Path backupRoot = Paths.get(backup.getDestinationPath());

        // Reaproveita a allowlist compartilhada para impedir restauracoes fora das raizes aprovadas.
        pathSecurityService.validateWritableManagedPath(request.getTargetPath(), "restauracao");
        validateRestorePath(request.getTargetPath());
        validateSelectedFiles(request.getSelectedFiles(), backupRoot);

        RestoreTask task = createRestoreTask(
                backup,
                request.getTargetPath(),
                RestoreType.SELECTIVE,
                serializeSelectedFiles(request.getSelectedFiles())
        );
        task = restoreRepository.save(task);

        // Usa o proxy Spring para que @Async seja realmente aplicado.
        self.processRestoreAsync(task, request.isOverwriteExisting(), request.getSelectedFiles());
        return task.getId();
    }

    @Async
    public void processRestoreAsync(RestoreTask task, boolean overwriteExisting,
                                    List<String> selectedFiles) {
        try {
            logger.info("Iniciando processamento de restauração: ID={}", task.getId());

            taskManager.registerTask(task.getId(), task);
            eventPublisher.publishEvent(new RestoreStartedEvent(task));

            Path backupSource = Paths.get(task.getSourceBackup().getDestinationPath());
            Path targetDestination = Paths.get(task.getTargetPath());

            try {
                FileRestoreOperations.RestoreResult result;

                if (task.getRestoreType() == RestoreType.FULL) {
                    result = restoreOps.restoreAll(backupSource, targetDestination,
                            overwriteExisting, createCallback(task.getId()));
                } else {
                    result = restoreOps.restoreSelective(backupSource, targetDestination,
                            selectedFiles, overwriteExisting,
                            createCallback(task.getId()));
                }

                RestoreTask memoryTask = taskManager.getTask(task.getId());

                if (memoryTask != null &&
                        (memoryTask.isCancelled() || memoryTask.getStatus() == RestoreStatus.CANCELADO)) {
                    logger.info("Restauração {} cancelada durante execução", task.getId());
                } else {
                    finalizeRestore(task, result);
                }

            } catch (Exception e) {
                RestoreTask memoryTask = taskManager.getTask(task.getId());

                if (memoryTask != null &&
                        (memoryTask.isCancelled() || memoryTask.getStatus() == RestoreStatus.CANCELADO)) {
                    logger.info("Restauração {} cancelada (exception capturada)", task.getId());
                } else {
                    handleRestoreFailure(task, e);
                }
            } finally {
                RestoreTask memoryTask = taskManager.getTask(task.getId());

                if (memoryTask != null &&
                        (memoryTask.isCancelled() || memoryTask.getStatus() == RestoreStatus.CANCELADO)) {
                    logger.info("Finally: Restauração {} cancelada, não sobrescreve estado", task.getId());
                    taskManager.unregisterTask(task.getId());
                    return;
                }

                RestoreTask finalTask = restoreRepository.findById(task.getId()).orElse(task);

                if (finalTask.getFinishedAt() == null) {
                    finalTask.setFinishedAt(LocalDateTime.now());
                    restoreRepository.save(finalTask);
                }

                taskManager.unregisterTask(task.getId());
            }

        } catch (Exception e) {
            logger.error("Erro crítico no processamento da restauração {}: {}",
                    task.getId(), e.getMessage(), e);
        }
    }

    private FileRestoreOperations.RestoreProgressCallback createCallback(Long taskId) {
        return new FileRestoreOperations.RestoreProgressCallback() {
            @Override
            public void onFileRestored(Path file, BasicFileAttributes attrs) {}

            @Override
            public void onWarning(String message, Path path) {
                logger.warn("Restauração {}: {} - {}", taskId, message, path);
            }

            @Override
            public boolean shouldContinue() {
                RestoreTask task = taskManager.getTask(taskId);
                return task != null && !task.isCancelled();
            }

            @Override
            public void onProgress(int processed, int total) {
                taskManager.updateProgress(taskId, processed);
            }
        };
    }

    private void finalizeRestore(RestoreTask task, FileRestoreOperations.RestoreResult result) {
        logger.info("Finalizando restauração {}: {} arquivos restaurados",
                task.getId(), result.getFilesRestored());

        // Define o timestamp antes do cálculo para não publicar duração zerada nos eventos.
        task.setFinishedAt(LocalDateTime.now());
        long durationSeconds = calculateDuration(task);

        task.setFileCount((long) result.getFilesRestored());
        task.setRestoredFiles((long) result.getFilesRestored());
        task.setTotalSizeMB(BigDecimal.valueOf(result.getTotalBytes())
                .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP));
        task.setStatus(RestoreStatus.CONCLUIDO);

        if (result.getWarnings() > 0) {
            task.setErrorMessage("Concluído com " + result.getWarnings() + " avisos");
        }

        restoreRepository.save(task);
        eventPublisher.publishEvent(new RestoreCompletedEvent(task, durationSeconds));
    }

    private void handleRestoreFailure(RestoreTask task, Exception e) {
        logger.error("Falha na restauração {}: {}", task.getId(), e.getMessage());

        task.setStatus(RestoreStatus.FALHA);
        task.setErrorMessage(e.getMessage());
        task.setFinishedAt(LocalDateTime.now());

        restoreRepository.save(task);
        eventPublisher.publishEvent(new RestoreFailedEvent(task, e.getMessage()));
    }

    private BackupTask validateBackup(Long backupId) {
        BackupTask backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup não encontrado: " + backupId));

        Path backupPath = Paths.get(backup.getDestinationPath());
        if (!Files.exists(backupPath)) {
            throw new IllegalStateException("Backup não encontrado no disco: " + backupPath);
        }

        return backup;
    }

    private void validateRestorePath(String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("Caminho de destino não pode estar vazio");
        }

        Path normalized = Paths.get(targetPath).normalize().toAbsolutePath();

        if (targetPath.contains("..")) {
            throw new SecurityException("Path traversal detectado: " + targetPath);
        }

        String normalizedPath = normalized.toString().toLowerCase();
        String rootDir = System.getenv("SystemRoot");
        String windowsDir = (rootDir != null) ? rootDir.toLowerCase() : "c:\\windows";

        boolean isForbidden = normalizedPath.startsWith(windowsDir) ||
                normalizedPath.contains("system32") ||
                normalizedPath.contains("syswow64") ||
                normalizedPath.contains("program files") ||
                normalizedPath.matches("^[a-z]:\\\\$");

        if (isForbidden) {
            throw new SecurityException("Restauração em pasta do sistema não permitida: " + targetPath);
        }

        Path parent = normalized.getParent();
        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new SecurityException("Sem permissão de escrita no destino: " + targetPath);
        }
    }

    private void validateSelectedFiles(List<String> selectedFiles, Path backupRoot) {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            throw new IllegalArgumentException("Lista de arquivos selecionados não pode estar vazia");
        }

        for (String file : selectedFiles) {
            Path filePath = backupRoot.resolve(file).normalize();

            if (!filePath.startsWith(backupRoot)) {
                throw new SecurityException("Path fora do backup: " + file);
            }
        }
    }

    private RestoreTask createRestoreTask(BackupTask backup, String targetPath,
                                          RestoreType type, String selectedFiles) {
        RestoreTask task = new RestoreTask();
        task.setSourceBackup(backup);
        task.setTargetPath(targetPath);
        task.setRestoreType(type);
        task.setSelectedFiles(selectedFiles);
        task.setStatus(RestoreStatus.EM_ANDAMENTO);
        task.setStartedAt(LocalDateTime.now());
        return task;
    }

    private String serializeSelectedFiles(List<String> selectedFiles) {
        try {
            return objectMapper.writeValueAsString(selectedFiles);
        } catch (JsonProcessingException e) {
            logger.error("Erro ao serializar arquivos selecionados: {}", e.getMessage());
            return "[]";
        }
    }

    private long calculateDuration(RestoreTask task) {
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            return java.time.temporal.ChronoUnit.SECONDS.between(
                    task.getStartedAt(), task.getFinishedAt()
            );
        }
        return 0;
    }

    public boolean cancelRestore(Long taskId) {
        return taskManager.cancelTask(taskId);
    }

    public List<RestoreTask> getAllRestores() {
        return restoreRepository.findAll();
    }
}
