package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.domain.event.ScheduledBackupFailedEvent;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

@Service
public class DynamicSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSchedulerService.class);

    private final ScheduledBackupRepository repository;
    private final BackupScheduler backupScheduler;
    private final BackupRequestValidationService backupRequestValidationService;
    private final TaskScheduler taskScheduler;
    private final BackupSchedulerProperties schedulerProperties;
    private final CronValidationService cronValidationService;
    private final Executor dispatchExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    // Agendamentos cuja execucao ainda esta na fila ou validando: um novo disparo nao empilha outra.
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public DynamicSchedulerService(ScheduledBackupRepository repository,
                                   BackupScheduler backupScheduler,
                                   BackupRequestValidationService backupRequestValidationService,
                                   @Qualifier("backupOneTimeScheduler") TaskScheduler taskScheduler,
                                   BackupSchedulerProperties schedulerProperties,
                                   CronValidationService cronValidationService,
                                   @Qualifier("backupDispatchExecutor") Executor dispatchExecutor,
                                   ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.backupScheduler = backupScheduler;
        this.backupRequestValidationService = backupRequestValidationService;
        this.taskScheduler = taskScheduler;
        this.schedulerProperties = schedulerProperties;
        this.cronValidationService = cronValidationService;
        this.dispatchExecutor = dispatchExecutor;
        this.eventPublisher = eventPublisher;
    }

    // O carregamento ocorre apos o boot completo: o scheduler e injetado diretamente,
    // sem depender do ScheduledTaskRegistrar (que ainda nao tem scheduler em configureTasks).
    @EventListener(ApplicationReadyEvent.class)
    public void loadScheduledBackupsOnStartup() {
        refreshAllTasks();
    }

    public synchronized void refreshAllTasks() {
        logger.info("Atualizando agendamentos recorrentes");

        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();

        List<ScheduledBackupEntity> activeBackups = repository.findByEnabledTrue();
        logger.info("Encontrados {} agendamentos ativos", activeBackups.size());

        for (ScheduledBackupEntity config : activeBackups) {
            scheduleTask(config);
        }
    }

    private void scheduleTask(ScheduledBackupEntity config) {
        if (config.getCronExpression() == null || config.getCronExpression().trim().isEmpty()) {
            logger.error("Falha ao agendar '{}': expressao cron vazia no banco.", config.getName());
            return;
        }

        try {
            backupRequestValidationService.validatePathPolicy(
                    config.getSources(),
                    config.getDestinations()
            );
        } catch (RuntimeException e) {
            logger.warn("Agendamento '{}' ignorado por configuracao invalida: {}", config.getName(), e.getMessage());
            return;
        }

        try {
            String intervalError = cronValidationService.minimumIntervalError(config.getCronExpression());
            if (intervalError != null) {
                logger.warn("Agendamento '{}' ignorado: {} (cron {})",
                        config.getName(), intervalError, config.getCronExpression());
                return;
            }

            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> dispatchScheduledBackup(config),
                    new CronTrigger(config.getCronExpression(), schedulerProperties.zoneId())
            );

            if (future == null) {
                logger.warn("Agendamento '{}' ignorado: expressao cron sem proxima execucao ({})",
                        config.getName(), config.getCronExpression());
                return;
            }

            scheduledTasks.put(config.getId(), future);
            logger.info("Agendamento '{}' registrado com expressao cron: {}",
                    config.getName(), config.getCronExpression());

        } catch (IllegalArgumentException e) {
            logger.error("Expressao cron invalida para o backup '{}': {}",
                    config.getName(), config.getCronExpression());
        }
    }

    // A thread do agendador so repassa: a validacao do disparo estima espaco varrendo origem e destino,
    // e fazer isso aqui atrasava os demais agendamentos do pool.
    void dispatchScheduledBackup(ScheduledBackupEntity config) {
        if (!inFlight.add(config.getId())) {
            logger.warn("Execucao agendada '{}' ignorada: a anterior ainda esta sendo preparada", config.getName());
            return;
        }

        try {
            dispatchExecutor.execute(() -> {
                try {
                    executeScheduledBackup(config);
                } finally {
                    inFlight.remove(config.getId());
                }
            });
        } catch (TaskRejectedException e) {
            inFlight.remove(config.getId());
            logger.error("Execucao agendada '{}' descartada: fila de disparo cheia", config.getName());
            eventPublisher.publishEvent(new ScheduledBackupFailedEvent(config.getName(),
                    List.of("Fila de disparo de backups cheia; execucao descartada.")));
        }
    }

    void executeScheduledBackup(ScheduledBackupEntity config) {
        logger.info("Iniciando execucao agendada: {}", config.getName());
        try {
            BackupRequest request = new BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            List<Long> taskIds = backupScheduler.executeScheduledRun(request, config.getName());
            if (taskIds.isEmpty()) {
                logger.warn("Execucao agendada '{}' nao iniciou nenhuma tarefa", config.getName());
                return;
            }

            updateLastExecution(config.getId());
            logger.info("Execucao agendada concluida: {}, taskIds={}", config.getName(), taskIds);
        } catch (Exception e) {
            logger.error("Erro ao executar backup agendado '{}': {}", config.getName(), e.getMessage(), e);
        }
    }

    private void updateLastExecution(Long configId) {
        try {
            repository.findById(configId).ifPresent(config -> {
                config.setLastExecution(LocalDateTime.now());
                repository.save(config);
                logger.debug("LastExecution atualizado para agendamento ID {}", configId);
            });
        } catch (Exception e) {
            logger.error("Erro ao atualizar lastExecution para ID {}: {}", configId, e.getMessage());
        }
    }
}
