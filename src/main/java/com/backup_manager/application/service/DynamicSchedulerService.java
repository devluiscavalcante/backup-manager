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

    private final ScheduledBackupRepository repository;
    private final BackupScheduler backupScheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private ScheduledTaskRegistrar taskRegistrar;

    private static final Logger logger = LoggerFactory.getLogger(DynamicSchedulerService.class);

    public DynamicSchedulerService(ScheduledBackupRepository repository, BackupScheduler backupScheduler) {
        this.repository = repository;
        this.backupScheduler = backupScheduler;
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
            logger.error("Falha ao agendar '{}': Expressão Cron está vazia no banco.", config.getName());
            return;
        }

        if (taskRegistrar != null && taskRegistrar.getScheduler() != null) {
            try {
                ScheduledFuture<?> future = taskRegistrar.getScheduler().schedule(
                        () -> executeScheduledBackup(config),
                        new CronTrigger(config.getCronExpression())
                );

                scheduledTasks.put(config.getId(), future);
                logger.info("Agendamento '{}' registrado com expressão cron: {}",
                        config.getName(), config.getCronExpression());

            } catch (IllegalArgumentException e) {
                logger.error("Expressão Cron inválida para o backup '{}': {}",
                        config.getName(), config.getCronExpression());
            }
        }
    }

    private void executeScheduledBackup(ScheduledBackupEntity config) {
        logger.info("Iniciando execução agendada: {}", config.getName());

        try {
            com.backup_manager.application.dto.BackupRequest request =
                    new com.backup_manager.application.dto.BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            backupScheduler.executeBackupWithRequest(request, config.getName());

            updateLastExecution(config.getId());

            logger.info("Execução agendada concluída: {}", config.getName());

        } catch (Exception e) {
            logger.error("Erro ao executar backup agendado '{}': {}",
                    config.getName(), e.getMessage(), e);
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
            logger.error("Erro ao atualizar lastExecution para ID {}: {}",
                    configId, e.getMessage());
        }
    }
}