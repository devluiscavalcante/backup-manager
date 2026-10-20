package com.backup_manager.domain.service;

import com.backup_manager.domain.event.BackupCancelledEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Component
public class BackupTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(BackupTaskManager.class);
    private static final int MAX_TRANSITION_ATTEMPTS = 3;

    private final Map<Long, AtomicReference<BackupTask>> runningTasks = new ConcurrentHashMap<>();
    private final BackupRepository backupRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BackupTaskManager(BackupRepository backupRepository, ApplicationEventPublisher eventPublisher) {
        this.backupRepository = backupRepository;
        this.eventPublisher = eventPublisher;
    }

    // Chamado quando a tarefa sai da fila do executor. A referencia e registrada antes de reler o banco,
    // assim pausas/cancelamentos feitos enquanto a tarefa aguardava (ou durante a releitura) sao preservados.
    public BackupTask activateQueuedTask(Long taskId, BackupTask queuedTask) {
        AtomicReference<BackupTask> ref = new AtomicReference<>(queuedTask);
        runningTasks.put(taskId, ref);

        backupRepository.findById(taskId)
                .ifPresent(persistedTask -> ref.compareAndSet(queuedTask, persistedTask));

        BackupTask currentTask = ref.get();
        logger.info("Tarefa ativada: ID={}, Status={}", taskId, currentTask.getStatus());
        return currentTask;
    }

    public BackupTask getTask(Long taskId) {
        AtomicReference<BackupTask> ref = runningTasks.get(taskId);
        if (ref != null) {
            return ref.get();
        }

        // Se não está na memória, busca do banco
        return backupRepository.findById(taskId).orElse(null);
    }

    public boolean pauseTask(Long taskId) {
        logger.info("Pausando backup: ID={}", taskId);

        Optional<BackupTask> paused = transition(taskId, "pausado", dbTask -> {
            if (dbTask.getStatus() != Status.EM_ANDAMENTO) {
                return false;
            }
            dbTask.setPaused(true);
            dbTask.setStatus(Status.PAUSADO);
            dbTask.setPausedAt(LocalDateTime.now());
            return true;
        });

        paused.ifPresent(task -> logger.info("Backup {} pausado com sucesso", taskId));
        return paused.isPresent();
    }

    public boolean resumeTask(Long taskId) {
        logger.info("Retomando backup: ID={}", taskId);

        Optional<BackupTask> resumed = transition(taskId, "retomado", dbTask -> {
            if (dbTask.getStatus() != Status.PAUSADO) {
                return false;
            }
            dbTask.setPaused(false);
            dbTask.setStatus(Status.EM_ANDAMENTO);
            return true;
        });

        resumed.ifPresent(task -> logger.info("Backup {} retomado com sucesso", taskId));
        return resumed.isPresent();
    }

    public boolean cancelTask(Long taskId) {
        logger.info("Cancelando backup: ID={}", taskId);

        Optional<BackupTask> cancelled = transition(taskId, "cancelado", dbTask -> {
            if (dbTask.getStatus() != Status.EM_ANDAMENTO && dbTask.getStatus() != Status.PAUSADO) {
                return false;
            }
            dbTask.setCancelled(true);
            dbTask.setStatus(Status.CANCELADO);
            dbTask.setFinishedAt(LocalDateTime.now());
            dbTask.setErrorMessage("Backup cancelado pelo usuário");
            return true;
        });

        cancelled.ifPresent(task -> {
            eventPublisher.publishEvent(new BackupCancelledEvent(task));
            logger.info("Backup {} cancelado com sucesso", taskId);
        });
        return cancelled.isPresent();
    }

    /**
     * Aplica uma mudanca de estado sobre a versao atual do banco. Se outra escrita (o worker finalizando
     * ou outro comando) alterar a tarefa entre a leitura e o save, o @Version recusa o save e a regra e
     * reavaliada sobre o estado novo, em vez de sobrescreve-lo.
     */
    private Optional<BackupTask> transition(Long taskId, String targetState, Predicate<BackupTask> change) {
        for (int attempt = 1; attempt <= MAX_TRANSITION_ATTEMPTS; attempt++) {
            try {
                Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
                if (dbTaskOpt.isEmpty()) {
                    logger.warn("Backup {} não encontrado", taskId);
                    return Optional.empty();
                }

                BackupTask dbTask = dbTaskOpt.get();
                Status currentStatus = dbTask.getStatus();
                if (!change.test(dbTask)) {
                    logger.warn("Backup {} não pode ser {}. Status: {}", taskId, targetState, currentStatus);
                    return Optional.empty();
                }

                BackupTask saved = backupRepository.saveAndFlush(dbTask);

                AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
                if (taskRef != null) {
                    taskRef.set(saved);
                }
                return Optional.of(saved);

            } catch (OptimisticLockingFailureException e) {
                logger.info("Backup {} alterado concorrentemente, reavaliando (tentativa {}/{})",
                        taskId, attempt, MAX_TRANSITION_ATTEMPTS);
            } catch (Exception e) {
                logger.error("Erro ao alterar backup {} para {}: {}", taskId, targetState, e.getMessage(), e);
                return Optional.empty();
            }
        }

        logger.warn("Backup {} nao foi {}: conflito de concorrencia persistente", taskId, targetState);
        return Optional.empty();
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