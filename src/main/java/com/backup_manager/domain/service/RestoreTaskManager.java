package com.backup_manager.domain.service;

import com.backup_manager.domain.event.RestoreCancelledEvent;
import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
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
public class RestoreTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(RestoreTaskManager.class);

    private final Map<Long, AtomicReference<RestoreTask>> runningTasks = new ConcurrentHashMap<>();
    private final RestoreRepository restoreRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RestoreTaskManager(RestoreRepository restoreRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.restoreRepository = restoreRepository;
        this.eventPublisher = eventPublisher;
    }

    public void registerTask(Long taskId, RestoreTask task) {
        runningTasks.put(taskId, new AtomicReference<>(task));
        logger.info("Tarefa de restauração registrada: ID={}, Status={}", taskId, task.getStatus());
    }

    public RestoreTask getTask(Long taskId) {
        AtomicReference<RestoreTask> ref = runningTasks.get(taskId);
        if (ref != null) {
            return ref.get();
        }

        return restoreRepository.findById(taskId).orElse(null);
    }

    @Transactional
    public boolean cancelTask(Long taskId) {
        logger.info("Cancelando restauração: ID={}", taskId);

        try {
            Optional<RestoreTask> dbTaskOpt = restoreRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.warn("Restauração {} não encontrada", taskId);
                return false;
            }

            RestoreTask dbTask = dbTaskOpt.get();

            if (dbTask.getStatus() != RestoreStatus.EM_ANDAMENTO) {
                logger.warn("Restauração {} não pode ser cancelada. Status: {}",
                        taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime cancelTime = LocalDateTime.now();

            dbTask.setCancelled(true);
            dbTask.setStatus(RestoreStatus.CANCELADO);
            dbTask.setFinishedAt(cancelTime);
            dbTask.setErrorMessage("Restauração cancelada pelo usuário");

            restoreRepository.save(dbTask);
            restoreRepository.flush();

            // Atualizar memória
            AtomicReference<RestoreTask> taskRef = runningTasks.get(taskId);
            if (taskRef != null) {
                taskRef.set(dbTask);
            }

            eventPublisher.publishEvent(new RestoreCancelledEvent(dbTask));
            logger.info("Restauração {} cancelada com sucesso", taskId);

            return true;

        } catch (Exception e) {
            logger.error("Erro ao cancelar restauração {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    public void unregisterTask(Long taskId) {
        runningTasks.remove(taskId);
        logger.info("Tarefa de restauração {} removida da memória", taskId);
    }

    public void updateProgress(Long taskId, long restoredFiles) {
        AtomicReference<RestoreTask> taskRef = runningTasks.get(taskId);
        if (taskRef != null) {
            RestoreTask task = taskRef.get();
            task.setRestoredFiles(restoredFiles);
        }
    }

    public void logMemoryTasks() {
        logger.info("Tarefas de restauração na memória: {}", runningTasks.size());
        runningTasks.forEach((id, ref) -> {
            RestoreTask task = ref.get();
            if (task != null) {
                logger.info("  ID={}, Status={}, Progresso={}/{}",
                        id, task.getStatus(), task.getRestoredFiles(), task.getFileCount());
            }
        });
    }
}