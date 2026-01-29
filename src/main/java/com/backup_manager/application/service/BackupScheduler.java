package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.SchedulerStatus;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties.ScheduledBackupConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupSchedulerProperties schedulerProperties;

    private final Map<String, LocalDateTime> lastExecutionMap = new HashMap<>();
    private static final long MIN_INTERVAL_MINUTES = 5;

    public BackupScheduler(BackupService backupService, BackupSchedulerProperties schedulerProperties) {
        this.backupService = backupService;
        this.schedulerProperties = schedulerProperties;
    }

    @Scheduled(cron = "${backup.scheduler.cron-expression:0 0 2 * * *}",
            zone = "${backup.scheduler.time-zone:America/Sao_Paulo}")

    @Transactional
    public void executeScheduledBackups() {
        try {
            if (!schedulerProperties.isEnabled()) {
                logger.debug("Agendamento de backups está desativado na configuração global");
                return;
            }

            List<ScheduledBackupConfig> backups = schedulerProperties.getScheduledBackups();
            if (backups == null || backups.isEmpty()) {
                logger.debug("Nenhum backup agendado configurado");
                return;
            }

            logger.info("Iniciando execução de backups agendados. Total configurado: {}", backups.size());

            AtomicInteger executedCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);

            backups.forEach(config -> {
                try {
                    if (executeSingleScheduledBackup(config)) {
                        executedCount.incrementAndGet();
                    } else {
                        skippedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.error("Erro ao executar backup agendado '{}': {}", config.getName(), e.getMessage(), e);
                }
            });

            logger.info("Execução de backups agendados concluída. Executados: {}, Ignorados: {}",
                    executedCount.get(),
                    skippedCount.get());

        } catch (Exception e) {
            logger.error("Erro geral na execução de backups agendados: {}", e.getMessage(), e);
        }
    }

    private boolean executeSingleScheduledBackup(ScheduledBackupConfig config) {
        try {
            if (!config.isEnabled()) {
                logger.debug("Backup agendado '{}' está desativado", config.getName());
                return false;
            }

            if (config.getSources() == null || config.getSources().isEmpty() ||
                    config.getDestinations() == null ||
                    config.getDestinations().isEmpty()) {
                logger.warn("Configuração inválida para backup agendado '{}'", config.getName());
                return false;
            }

            String backupKey = config.getName();
            LocalDateTime lastExecution = lastExecutionMap.get(backupKey);
            LocalDateTime now = LocalDateTime.now();

            if (lastExecution != null && lastExecution.plusMinutes(MIN_INTERVAL_MINUTES).isAfter(now)) {
                logger.debug("Backup '{}' executado recentemente, ignorando execução", config.getName());
                return false;
            }

            logger.info("Iniciando backup agendado '{}' - Fontes: {}, Destinos: {}",
                    config.getName(),
                    config.getSources().size(),
                    config.getDestinations().size());

            BackupRequest request = new BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            executeBackupWithRequest(request, config.getName());

            lastExecutionMap.put(backupKey, now);

            logger.info("Backup agendado '{}' concluído com sucesso", config.getName());
            return true;

        } catch (Exception e) {
            logger.error("Falha ao executar backup agendado '{}': {}", config.getName(), e.getMessage(), e);
            return false;
        }
    }

    public  void executeBackupWithRequest(BackupRequest request, String backupName) {
        List<String> sources = request.getSources();
        List<String> destinations = request.getDestination();

        if (sources == null || destinations == null || sources.isEmpty() || destinations.isEmpty()) {
            throw new IllegalArgumentException("Fontes e destinos não podem estar vazios");
        }

        if (sources.size() != destinations.size()) {
            throw new IllegalArgumentException("Número de origens deve ser igual ao número de destinos");
        }

        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            String destination = destinations.get(i);

            try {
                var activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    logger.warn("Já existe backup ativo para {}. Ignorando execução agendada.",
                            source + " -> " + destination);
                    continue;
                }

                logger.info("Executando backup agendado '{}': {} -> {}", backupName, source, destination);
                backupService.runBackup(source, destination);

            } catch (Exception e) {
                logger.error("Erro ao executar backup agendado para {}: {}", source + " -> "
                        + destination, e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *") // Meia-noite diariamente
    public void cleanupExecutionHistory() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            lastExecutionMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            logger.debug("Histórico de execuções limpo. Entradas atuais: {}", lastExecutionMap.size());
        } catch (Exception e) {
            logger.warn("Erro ao limpar histórico de execuções: {}", e.getMessage());
        }
    }

    public SchedulerStatus getSchedulerStatus() {
        List<ScheduledBackupConfig> backups = schedulerProperties.getScheduledBackups();
        int enabledCount = backups != null ?
                (int) backups.stream().filter(ScheduledBackupConfig::isEnabled).count() : 0;

        return new SchedulerStatus(
                schedulerProperties.isEnabled(),
                schedulerProperties.getCronExpression(),
                schedulerProperties.getTimeZone(),
                backups != null ? backups.size() : 0,
                enabledCount,
                lastExecutionMap.size()
        );
    }
}
