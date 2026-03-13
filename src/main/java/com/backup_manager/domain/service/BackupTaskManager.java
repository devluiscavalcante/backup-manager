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
        logger.info("PAUSE iniciado para tarefa: {}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.error("Tarefa {} não encontrada no banco", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.EM_ANDAMENTO) {
                logger.warn("Tarefa {} não pode ser pausada. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime pauseTime = LocalDateTime.now();

            dbTask.setPaused(true);
            dbTask.setStatus(Status.PAUSADO);
            dbTask.setPausedAt(pauseTime);

            logger.info("Salvando pausa: ID={}, Time={}", taskId, pauseTime);

            backupRepository.save(dbTask);
            backupRepository.flush();

            Optional<BackupTask> verified = backupRepository.findById(taskId);
            if (verified.isPresent()) {
                BackupTask v = verified.get();
                if (v.getPausedAt() == null) {
                    logger.error("ERRO CRITICO: paused_at ainda NULL apos salvar");
                    v.setPausedAt(pauseTime);
                    backupRepository.save(v);
                }
                logger.info("Verificado: Status={}, PausedAt={}", v.getStatus(), v.getPausedAt());
            }

            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
                logger.info("Instancia em memoria atualizada: isPaused={}, Status={}",
                        dbTask.isPaused(), dbTask.getStatus());
            }

            logger.info("PAUSE salvo: ID={}, Status={}", taskId, dbTask.getStatus());
            return true;

        } catch (Exception e) {
            logger.error("ERRO ao pausar tarefa {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean resumeTask(Long taskId) {
        logger.info("RESUME iniciado para tarefa: {}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.error("Tarefa {} não encontrada no banco", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.PAUSADO) {
                logger.warn("Tarefa {} não pode ser retomada. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            dbTask.setPaused(false);
            dbTask.setStatus(Status.EM_ANDAMENTO);

            backupRepository.save(dbTask);
            backupRepository.flush();

            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
                logger.info("Instancia em memoria atualizada: isPaused={}, Status={}",
                        dbTask.isPaused(), dbTask.getStatus());
            }

            logger.info("RESUME salvo: ID={}, Status={}", taskId, dbTask.getStatus());
            return true;

        } catch (Exception e) {
            logger.error("ERRO ao retomar tarefa {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean cancelTask(Long taskId) {
        logger.info("CANCEL iniciado para tarefa: {}", taskId);

        try {
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.error("Tarefa {} não encontrada no banco", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != Status.EM_ANDAMENTO &&
                    dbTask.getStatus() != Status.PAUSADO) {
                logger.warn("Tarefa {} não pode ser cancelada. Status: {}", taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime cancelTime = LocalDateTime.now();

            dbTask.setCancelled(true);
            dbTask.setStatus(Status.CANCELADO);
            dbTask.setFinishedAt(cancelTime);
            dbTask.setErrorMessage("Backup cancelado pelo usuário");

            if (dbTask.getStatus() == Status.PAUSADO && dbTask.getPausedAt() == null) {
                dbTask.setPausedAt(cancelTime.minusSeconds(10));
                logger.warn("Definindo paused_at retroativamente para tarefa {}", taskId);
            }

            backupRepository.save(dbTask);
            backupRepository.flush();

            AtomicReference<BackupTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
                logger.info("Instancia em memoria atualizada: isCancelled={}, Status={}",
                        dbTask.isCancelled(), dbTask.getStatus());
            }

            eventPublisher.publishEvent(new BackupCancelledEvent(dbTask));
            logger.info("Evento BackupCancelledEvent publicado para task {}", taskId);

            logger.info("CANCEL salvo: ID={}, Status={}", taskId, dbTask.getStatus());
            return true;

        } catch (Exception e) {
            logger.error("ERRO ao cancelar tarefa {}: {}", taskId, e.getMessage(), e);
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