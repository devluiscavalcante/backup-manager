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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Component
public class BackupTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(BackupTaskManager.class);
    private static final int MAX_TRANSITION_ATTEMPTS = 3;

    private final Map<Long, AtomicReference<BackupTask>> runningTasks = new ConcurrentHashMap<>();
    // Serializa "worker solta a tarefa pausada" com "retomada verifica se ha worker": sem isso uma
    // retomada no meio da saida do worker deixaria a tarefa EM_ANDAMENTO sem ninguem copiando.
    private final Object workerLock = new Object();
    // Tarefas com worker despachado, ainda na fila do executor ou ja rodando. runningTasks so recebe a
    // tarefa quando ela sai da fila, entao nao serve para saber se uma retomada precisa de worker novo.
    private final Set<Long> workers = ConcurrentHashMap.newKeySet();
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

    /**
     * Reserva o worker da tarefa antes de despacha-lo. Devolve false se ja ha um (por exemplo, uma
     * retomada despachou primeiro), caso em que quem chamou nao deve despachar outro.
     */
    public boolean claimWorker(Long taskId) {
        synchronized (workerLock) {
            return workers.add(taskId);
        }
    }

    // Desfaz claimWorker quando o executor recusa o despacho.
    public void releaseWorkerClaim(Long taskId) {
        synchronized (workerLock) {
            workers.remove(taskId);
        }
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

    /**
     * Um worker pausado solta a thread (veja {@link #releaseIfPaused}). Se a tarefa retomada nao tem mais
     * worker, {@code dispatchWorker} recebe a tarefa para coloca-la de volta na fila de execucao.
     */
    public boolean resumeTask(Long taskId, Consumer<BackupTask> dispatchWorker) {
        logger.info("Retomando backup: ID={}", taskId);

        Optional<BackupTask> resumed = transition(taskId, "retomado", dbTask -> {
            if (dbTask.getStatus() != Status.PAUSADO) {
                return false;
            }
            dbTask.setPaused(false);
            dbTask.setStatus(Status.EM_ANDAMENTO);
            return true;
        });
        if (resumed.isEmpty()) {
            return false;
        }

        synchronized (workerLock) {
            if (workers.add(taskId)) {
                logger.info("Backup {} sem worker ativo, voltando para a fila", taskId);
                try {
                    dispatchWorker.accept(resumed.get());
                } catch (RuntimeException rejected) {
                    workers.remove(taskId);
                    throw rejected;
                }
            }
        }
        logger.info("Backup {} retomado com sucesso", taskId);
        return true;
    }

    /**
     * Chamado pelo worker que interrompeu a copia por causa de uma pausa. Se a tarefa continua pausada,
     * ela sai da memoria e o worker pode encerrar, liberando a thread. Se foi retomada (ou cancelada)
     * nesse meio-tempo, devolve false com o estado atual ja visivel em {@link #getTask}.
     */
    public boolean releaseIfPaused(Long taskId) {
        synchronized (workerLock) {
            Optional<BackupTask> persisted = backupRepository.findById(taskId);
            if (persisted.isPresent() && persisted.get().getStatus() == Status.PAUSADO) {
                runningTasks.remove(taskId);
                workers.remove(taskId);
                logger.info("Backup {} pausado: worker liberado", taskId);
                return true;
            }

            AtomicReference<BackupTask> ref = runningTasks.get(taskId);
            if (ref != null && persisted.isPresent()) {
                ref.set(persisted.get());
            }
            return false;
        }
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
        synchronized (workerLock) {
            runningTasks.remove(taskId);
            workers.remove(taskId);
        }
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