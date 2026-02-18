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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupSchedulerProperties schedulerProperties;

    private final Map<String, LocalDateTime> lastExecutionMap = new HashMap<>();
    private static final long MIN_INTERVAL_MINUTES = 5;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService oneTimeScheduler = Executors.newScheduledThreadPool(3);

    private final AtomicLong taskIdCounter = new AtomicLong(1000);

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
                    logger.error("Erro ao executar backup agendado '{}': {}", config.getName(), e.getMessage());
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

            BackupRequest request = new BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            executeBackupWithRequest(request, config.getName());

            lastExecutionMap.put(backupKey, now);
            return true;

        } catch (Exception e) {
            logger.error("Falha ao preparar backup agendado '{}': {}", config.getName(), e.getMessage());
            return false;
        }
    }

    public Long scheduleOneTimeBackup(BackupRequest request, int minutesFromNow, String backupName) {
        if (minutesFromNow <= 0) {
            throw new IllegalArgumentException("Minutos devem ser maior que 0");
        }

        Long taskId = taskIdCounter.incrementAndGet();
        LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(minutesFromNow);

        logger.info("Agendando backup único ID {} para {}", taskId, scheduledTime);

        ScheduledFuture<?> future = oneTimeScheduler.schedule(() -> {
            try {
                logger.info("Executando backup único agendado ID {}: {}", taskId, backupName);
                executeBackupWithRequest(request, backupName);
                scheduledTasks.remove(taskId);
            } catch (Exception e) {
                logger.error("Erro no backup agendado ID {}: {}", taskId, e.getMessage());
                scheduledTasks.remove(taskId);
            }
        }, minutesFromNow, TimeUnit.MINUTES);

        scheduledTasks.put(taskId, future);
        return taskId;
    }

    public void executeBackupWithRequest(BackupRequest request, String backupName) {
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
                backupService.validateSafePath(source);
                backupService.validateSafePath(destination);
                backupService.validatePathAndDriveSpace(source, destination);

                var activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    logger.warn("Backup agendado '{}' ignorado: Já existe uma tarefa ativa para {} -> {}",
                            backupName, source, destination);
                    continue;
                }

                logger.info("Disparando execução agendada '{}': {} -> {}", backupName, source, destination);
                backupService.runBackup(source, destination);

            } catch (Exception e) {
                logger.error("Agendador pulou o par [{} -> {}] devido a erro: {}", source, destination, e.getMessage());
            }
        }
    }

    public boolean cancelScheduledBackup(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            boolean cancelled = future.cancel(false);
            scheduledTasks.remove(taskId);
            return cancelled;
        }
        return false;
    }

    public Map<Long, Map<String, Object>> getPendingScheduledBackups() {
        Map<Long, Map<String, Object>> pendingTasks = new HashMap<>();
        scheduledTasks.forEach((taskId, future) -> {
            if (!future.isDone() && !future.isCancelled()) {
                long delaySeconds = future.getDelay(TimeUnit.SECONDS);
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("status", "PENDENTE");
                taskInfo.put("remainingTime", formatRemainingTime(delaySeconds));
                taskInfo.put("cancelUrl", "/api/backup/scheduler/schedule/" + taskId + "/cancel");
                pendingTasks.put(taskId, taskInfo);
            }
        });
        return pendingTasks;
    }

    private String formatRemainingTime(long seconds) {
        if (seconds <= 0) return "Executando agora";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes == 0) return remainingSeconds + " segundos";
        return minutes + " minutos e " + remainingSeconds + " segundos";
    }

    @Scheduled(fixedDelay = 300000) // 5 minutos
    public void cleanupCompletedTasks() {
        scheduledTasks.entrySet().removeIf(entry -> entry.getValue().isDone()
                || entry.getValue().isCancelled());
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupExecutionHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        lastExecutionMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    public SchedulerStatus getSchedulerStatus() {
        List<ScheduledBackupConfig> backups = schedulerProperties.getScheduledBackups();
        int enabledCount = backups != null ? (int) backups.stream().filter(ScheduledBackupConfig::isEnabled).count() : 0;

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