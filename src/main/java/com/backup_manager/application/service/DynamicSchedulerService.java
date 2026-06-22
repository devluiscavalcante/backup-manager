package com.backup_manager.application.service;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Configuration
@Service
public class DynamicSchedulerService implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSchedulerService.class);

    private final ScheduledBackupRepository repository;
    private final BackupScheduler backupScheduler;
    private final BackupRequestValidationService backupRequestValidationService;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private ScheduledTaskRegistrar taskRegistrar;

    public DynamicSchedulerService(ScheduledBackupRepository repository,
                                   BackupScheduler backupScheduler,
                                   BackupRequestValidationService backupRequestValidationService) {
        this.repository = repository;
        this.backupScheduler = backupScheduler;
        this.backupRequestValidationService = backupRequestValidationService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        refreshAllTasks();
    }

    public void refreshAllTasks() {
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

        if (taskRegistrar != null && taskRegistrar.getScheduler() != null) {
            try {
                ScheduledFuture<?> future = taskRegistrar.getScheduler().schedule(
                        () -> executeScheduledBackup(config),
                        new CronTrigger(config.getCronExpression())
                );

                scheduledTasks.put(config.getId(), future);
                logger.info("Agendamento '{}' registrado com expressao cron: {}",
                        config.getName(), config.getCronExpression());

            } catch (IllegalArgumentException e) {
                logger.error("Expressao cron invalida para o backup '{}': {}",
                        config.getName(), config.getCronExpression());
            }
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
