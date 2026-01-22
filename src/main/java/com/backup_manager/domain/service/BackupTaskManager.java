package com.backup_manager.domain.service;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public BackupTaskManager(BackupRepository backupRepository) {
        this.backupRepository = backupRepository;
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
            // Busca do banco
            Optional<BackupTask> dbTaskOpt = backupRepository.findById(taskId);
            if (!dbTaskOpt.isPresent()) {
                logger.error("Tarefa {} não encontrada no banco", taskId);
                return false;
            }

            BackupTask dbTask = dbTaskOpt.get();

            // Valida se pode pausar
            if (dbTask.getStatus() != Status.EM_ANDAMENTO) {
                logger.warn("Tarefa {} não pode ser pausada. Status: {}",
                        taskId, dbTask.getStatus());
                return false;
            }

            // Define paused_at
            LocalDateTime pauseTime = LocalDateTime.now();

            // Atualiza
            dbTask.setPaused(true);
            dbTask.setStatus(Status.PAUSADO);
            dbTask.setPausedAt(pauseTime);

            logger.info("Salvando pausa: ID={}, Time={}", taskId, pauseTime);

            // Salva
            backupRepository.save(dbTask);
            backupRepository.flush();

            // Verifica se salvou corretamente
            Optional<BackupTask> verified = backupRepository.findById(taskId);
            if (verified.isPresent()) {
                BackupTask v = verified.get();
                if (v.getPausedAt() == null) {
                    logger.error("ERRO CRÍTICO: paused_at ainda NULL após salvar!");
                    v.setPausedAt(pauseTime);
                    backupRepository.save(v);
                }
                logger.info("Verificado: Status={}, PausedAt={}",
                        v.getStatus(), v.getPausedAt());
            }

            // Atualiza memória
            runningTasks.put(taskId, new AtomicReference<>(dbTask));

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
                logger.warn("Tarefa {} não pode ser retomada. Status: {}",
                        taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime resumeTime = LocalDateTime.now();

            dbTask.setPaused(false);
            dbTask.setStatus(Status.EM_ANDAMENTO);
            // Não limpa paused_at - manter histórico

            backupRepository.save(dbTask);
            backupRepository.flush();

            logger.info("RESUME salvo: ID={}, Status={}", taskId, dbTask.getStatus());

            runningTasks.put(taskId, new AtomicReference<>(dbTask));

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
                logger.warn("Tarefa {} não pode ser cancelada. Status: {}",
                        taskId, dbTask.getStatus());
                return false;
            }

            LocalDateTime cancelTime = LocalDateTime.now();

            dbTask.setCancelled(true);
            dbTask.setStatus(Status.CANCELADO);
            dbTask.setFinishedAt(cancelTime);

            if (dbTask.getStatus() == Status.PAUSADO && dbTask.getPausedAt() == null) {
                dbTask.setPausedAt(cancelTime.minusSeconds(10));
                logger.warn("Definindo paused_at retroativamente para tarefa {}", taskId);
            }

            backupRepository.save(dbTask);
            backupRepository.flush();

            logger.info("CANCEL salvo: ID={}, Status={}", taskId, dbTask.getStatus());

            runningTasks.remove(taskId);

            return true;

        } catch (Exception e) {
            logger.error("ERRO ao cancelar tarefa {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    public void unregisterTask(Long taskId) {
        runningTasks.remove(taskId);
        logger.info("Tarefa {} removida da memória", taskId);
    }

    // Método para debug
    public void logMemoryTasks() {
        logger.info("Tarefas na memória: {}", runningTasks.size());
        runningTasks.forEach((id, ref) -> {
            BackupTask task = ref.get();
            if (task != null) {
                logger.info("  ID={}, Status={}, Paused={}, Cancelled={}",
                        id, task.getStatus(), task.isPaused(), task.isCancelled());
            }
        });
    }
}