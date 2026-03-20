package com.backup_manager.domain.service;

import com.backup_manager.domain.event.BackupCancelledEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BackupTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(BackupTaskManager.class);

    private final Map<Long, AtomicReference<BackupTask>> runningTasks = new ConcurrentHashMap<>();
    private final BackupRepository backupRepository;
    private final ApplicationEventPublisher eventPublisher;


    public BackupTaskManager(BackupRepository backupRepository, ApplicationEventPublisher eventPublisher) {
        this.backupRepository = backupRepository;
        this.eventPublisher = eventPublisher;
    }

    public void registerTask(Long taskId, BackupTask task) {
        runningTasks.put(taskId, new AtomicReference<>(task));
        logger.info("Tarefa registrada: ID={}, Status={}", taskId, task.getStatus());
    }

    public BackupTask getTask(Long taskId) {
        AtomicReference<BackupTask> ref = runningTasks.get(taskId);
        if (ref != null) {
            return ref.get();
        }

        // Se não está na memória, busca do banco
        return backupRepository.findById(taskId).orElse(null);
    }

    @Transactional
    public boolean pauseTask(Long taskId) {
        logger.info("Pausando backup: ID={}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.warn("Backup {} não encontrado", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.EM_ANDAMENTO) {
                logger.warn("Backup {} não pode ser pausado. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime pauseTime = LocalDateTime.now();

            dbTask.setPaused(true);
            dbTask.setStatus(Status.PAUSADO);
            dbTask.setPausedAt(pauseTime);

            backupRepository.save(dbTask);
            backupRepository.flush();

            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
            }

            logger.info("Backup {} pausado com sucesso", taskId);
            return true;

        } catch (Exception e) {
            logger.error("Erro ao pausar backup {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean resumeTask(Long taskId) {
        logger.info("Retomando backup: ID={}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.warn("Backup {} não encontrado", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.PAUSADO) {
                logger.warn("Backup {} não pode ser retomado. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            dbTask.setPaused(false);
            dbTask.setStatus(Status.EM_ANDAMENTO);

            backupRepository.save(dbTask);
            backupRepository.flush();

            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
            }

            logger.info("Backup {} retomado com sucesso", taskId);
            return true;

        } catch (Exception e) {
            logger.error("Erro ao retomar backup {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean cancelTask(Long taskId) {
        logger.info("Cancelando backup: ID={}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.warn("Backup {} não encontrado", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.EM_ANDAMENTO &&
                    dbTask.getStatus() != Status.PAUSADO) {
                logger.warn("Backup {} não pode ser cancelado. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime cancelTime = LocalDateTime.now();

            dbTask.setCancelled(true);
            dbTask.setStatus(Status.CANCELADO);
            dbTask.setFinishedAt(cancelTime);
            dbTask.setErrorMessage("Backup cancelado pelo usuário");

            if (dbTask.getStatus() == Status.PAUSADO && dbTask.getPausedAt() == null) {
                dbTask.setPausedAt(cancelTime.minusSeconds(10));
            }

            backupRepository.save(dbTask);
            backupRepository.flush();

            // Atualizar memória
            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
            }

            eventPublisher.publishEvent(new BackupCancelledEvent(dbTask));
            logger.info("Backup {} cancelado com sucesso", taskId);

            return true;

        } catch (Exception e) {
            logger.error("Erro ao cancelar backup {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    public void unregisterTask(Long taskId) {
        runningTasks.remove(taskId);
        logger.info("Tarefa {} removida da memoria", taskId);
    }

    public void logMemoryTasks() {
        logger.info("Tarefas na memoria: {}", runningTasks.size());
        runningTasks.forEach((id, ref) -> {
            BackupTask task = ref.get();
            if (task != null) {
                logger.info("  ID={}, Status={}, Paused={}, Cancelled={}",
                        id, task.getStatus(), task.isPaused(), task.isCancelled());
            }
        });
    }
}