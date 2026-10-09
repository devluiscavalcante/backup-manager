package com.backup_manager.application.service;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class DynamicSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSchedulerService.class);

    private final ScheduledBackupRepository repository;
    private final BackupScheduler backupScheduler;
    private final BackupRequestValidationService backupRequestValidationService;
    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public DynamicSchedulerService(ScheduledBackupRepository repository,
                                   BackupScheduler backupScheduler,
                                   BackupRequestValidationService backupRequestValidationService,
                                   @Qualifier("backupOneTimeScheduler") TaskScheduler taskScheduler) {
        this.repository = repository;
        this.backupScheduler = backupScheduler;
        this.backupRequestValidationService = backupRequestValidationService;
        this.taskScheduler = taskScheduler;
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
            backupRequestValidationService.validateSchedulableRequest(
                    config.getSources(),
                    config.getDestinations()
            );
        } catch (RuntimeException e) {
            logger.warn("Agendamento '{}' ignorado por configuracao invalida: {}", config.getName(), e.getMessage());
            return;
        }

        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeScheduledBackup(config),
                    new CronTrigger(config.getCronExpression())
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

    void executeScheduledBackup(ScheduledBackupEntity config) {
        logger.info("Iniciando execucao agendada: {}", config.getName());
        try {
            com.backup_manager.application.dto.BackupRequest request =
                    new com.backup_manager.application.dto.BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            List<Long> taskIds = backupScheduler.executeBackupWithRequest(request, config.getName());
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
