package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.PendingScheduledBackupResponse;
import com.backup_manager.application.dto.SchedulerStatus;
import com.backup_manager.domain.event.ScheduledBackupFailedEvent;
import com.backup_manager.domain.model.OneTimeBackupEntity;
import com.backup_manager.domain.model.OneTimeBackupStatus;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties.ScheduledBackupConfig;
import com.backup_manager.infrastructure.persistence.OneTimeBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private static final long MIN_INTERVAL_MINUTES = 5;

    private final BackupService backupService;
    private final BackupRequestValidationService backupRequestValidationService;
    private final BackupSchedulerProperties schedulerProperties;
    private final ThreadPoolTaskScheduler backupOneTimeScheduler;
    private final ApplicationEventPublisher eventPublisher;
    private final OneTimeBackupRepository oneTimeBackupRepository;

    private final Map<String, LocalDateTime> lastExecutionMap = new ConcurrentHashMap<>();
    // Futuros em memoria das execucoes unicas; a fonte de verdade e a tabela one_time_backups.
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public BackupScheduler(BackupService backupService,
                           BackupRequestValidationService backupRequestValidationService,
                           @Qualifier("backupOneTimeScheduler") ThreadPoolTaskScheduler backupOneTimeScheduler,
                           BackupSchedulerProperties schedulerProperties,
                           ApplicationEventPublisher eventPublisher,
                           OneTimeBackupRepository oneTimeBackupRepository) {
        this.backupService = backupService;
        this.backupRequestValidationService = backupRequestValidationService;
        this.backupOneTimeScheduler = backupOneTimeScheduler;
        this.schedulerProperties = schedulerProperties;
        this.eventPublisher = eventPublisher;
        this.oneTimeBackupRepository = oneTimeBackupRepository;
    }

    @Scheduled(cron = "${backup.scheduler.cron-expression:0 0 2 * * *}",
            zone = "${backup.scheduler.time-zone:America/Sao_Paulo}")
    @Transactional
    public void executeScheduledBackups() {
        try {
            if (!schedulerProperties.isEnabled()) {
                logger.debug("Agendamento de backups esta desativado na configuracao global");
                return;
            }

            List<ScheduledBackupConfig> backups = schedulerProperties.getScheduledBackups();
            if (backups == null || backups.isEmpty()) {
                logger.debug("Nenhum backup agendado configurado");
                return;
            }

            logger.info("Iniciando execucao de backups agendados. Total configurado: {}", backups.size());

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

            logger.info("Execucao de backups agendados concluida. Executados: {}, Ignorados: {}",
                    executedCount.get(),
                    skippedCount.get());

        } catch (Exception e) {
            logger.error("Erro geral na execucao de backups agendados: {}", e.getMessage(), e);
        }
    }

    private boolean executeSingleScheduledBackup(ScheduledBackupConfig config) {
        try {
            if (!config.isEnabled()) {
                logger.debug("Backup agendado '{}' esta desativado", config.getName());
                return false;
            }

            String backupKey = config.getName();
            LocalDateTime lastExecution = lastExecutionMap.get(backupKey);
            LocalDateTime now = LocalDateTime.now();

            if (lastExecution != null && lastExecution.plusMinutes(MIN_INTERVAL_MINUTES).isAfter(now)) {
                logger.debug("Backup '{}' executado recentemente, ignorando execucao", config.getName());
                return false;
            }

            BackupRequest request = new BackupRequest();
            request.setSources(config.getSources());
            request.setDestination(config.getDestinations());

            List<Long> taskIds = executeScheduledRun(request, config.getName());
            if (taskIds.isEmpty()) {
                logger.warn("Backup agendado '{}' nao iniciou nenhuma tarefa", config.getName());
                return false;
            }

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

        backupRequestValidationService.validateExecutableRequest(request.getSources(), request.getDestination());

        OneTimeBackupEntity oneTime = new OneTimeBackupEntity();
        oneTime.setName(backupName);
        oneTime.setSources(new ArrayList<>(request.getSources()));
        oneTime.setDestinations(new ArrayList<>(request.getDestination()));
        oneTime.setScheduledFor(LocalDateTime.now().plusMinutes(minutesFromNow));
        oneTime = oneTimeBackupRepository.save(oneTime);

        logger.info("Agendando backup unico ID {} para {}", oneTime.getId(), oneTime.getScheduledFor());
        scheduleExecution(oneTime.getId(), toInstant(oneTime.getScheduledFor()));
        return oneTime.getId();
    }

    // Execucoes unicas ficam no banco: no boot as pendentes voltam a ser agendadas, e as que venceram
    // com a aplicacao parada rodam na hora (o pedido era "faca este backup", nao "so se eu estiver no ar").
    @EventListener(ApplicationReadyEvent.class)
    public void reschedulePendingOneTimeBackups() {
        Instant now = Instant.now();
        for (OneTimeBackupEntity pending
                : oneTimeBackupRepository.findByStatusOrderByScheduledForAsc(OneTimeBackupStatus.PENDENTE)) {
            Instant when = toInstant(pending.getScheduledFor());
            if (when.isBefore(now)) {
                logger.warn("Backup unico ID {} venceu em {} com a aplicacao parada: executando agora",
                        pending.getId(), pending.getScheduledFor());
                when = now;
            }
            scheduleExecution(pending.getId(), when);
        }
    }

    private void scheduleExecution(Long oneTimeId, Instant when) {
        ScheduledFuture<?> future = backupOneTimeScheduler.schedule(() -> runOneTime(oneTimeId), when);
        if (future != null) {
            scheduledTasks.put(oneTimeId, future);
        }
    }

    void runOneTime(Long oneTimeId) {
        try {
            OneTimeBackupEntity oneTime = oneTimeBackupRepository.findById(oneTimeId).orElse(null);
            if (oneTime == null || oneTime.getStatus() != OneTimeBackupStatus.PENDENTE) {
                logger.info("Backup unico ID {} nao esta mais pendente, execucao ignorada", oneTimeId);
                return;
            }

            logger.info("Executando backup unico agendado ID {}: {}", oneTimeId, oneTime.getName());
            BackupRequest request = new BackupRequest();
            request.setSources(oneTime.getSources());
            request.setDestination(oneTime.getDestinations());

            List<Long> taskIds = executeScheduledRun(request, oneTime.getName());
            oneTime.setExecutedAt(LocalDateTime.now());
            if (taskIds.isEmpty()) {
                logger.warn("Backup unico agendado ID {} nao iniciou nenhuma tarefa", oneTimeId);
                oneTime.setStatus(OneTimeBackupStatus.FALHA);
                oneTime.setErrorMessage("Nenhuma tarefa de backup foi iniciada.");
            } else {
                oneTime.setStatus(OneTimeBackupStatus.EXECUTADO);
            }
            oneTimeBackupRepository.save(oneTime);
        } catch (RuntimeException e) {
            logger.error("Erro no backup unico agendado ID {}: {}", oneTimeId, e.getMessage(), e);
        } finally {
            scheduledTasks.remove(oneTimeId);
        }
    }

    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Execucao disparada pelo agendador: nunca propaga excecao (nao ha quem a receba) e notifica
     * os pares que nao iniciaram, ja que sem tarefa criada nenhum BackupFailedEvent e publicado.
     */
    public List<Long> executeScheduledRun(BackupRequest request, String backupName) {
        List<String> failures = new ArrayList<>();
        List<Long> taskIds = List.of();

        try {
            taskIds = startPairs(request, backupName, failures);
        } catch (RuntimeException e) {
            logger.error("Execucao agendada '{}' nao iniciou: {}", backupName, e.getMessage());
            // Falhas por par ja foram registradas; vazio significa que a validacao previa recusou o pedido.
            if (failures.isEmpty()) {
                failures.add(e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            eventPublisher.publishEvent(new ScheduledBackupFailedEvent(backupName, failures));
        }
        return taskIds;
    }

    public List<Long> executeBackupWithRequest(BackupRequest request, String backupName) {
        return startPairs(request, backupName, new ArrayList<>());
    }

    private List<Long> startPairs(BackupRequest request, String backupName, List<String> failures) {
        List<String> sources = request.getSources();
        List<String> destinations = request.getDestination();
        List<Long> taskIds = new ArrayList<>();
        RuntimeException firstFailure = null;

        backupRequestValidationService.validateExecutableRequest(sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            String destination = destinations.get(i);

            try {
                var activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    logger.warn("Backup agendado '{}' ignorado: ja existe uma tarefa ativa para {} -> {}",
                            backupName, source, destination);
                    continue;
                }

                logger.info("Disparando execucao agendada '{}': {} -> {}", backupName, source, destination);
                taskIds.add(backupService.runBackup(source, destination));

            } catch (RuntimeException e) {
                logger.error("Agendador pulou o par [{} -> {}] devido a erro: {}", source, destination, e.getMessage());
                failures.add(source + " -> " + destination + ": " + e.getMessage());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (taskIds.isEmpty() && firstFailure != null) {
            throw firstFailure;
        }

        return taskIds;
    }

    public boolean cancelScheduledBackup(Long taskId) {
        OneTimeBackupEntity oneTime = oneTimeBackupRepository.findById(taskId).orElse(null);
        if (oneTime == null || oneTime.getStatus() != OneTimeBackupStatus.PENDENTE) {
            return false;
        }

        // Status primeiro: se o futuro ja estiver disparando, runOneTime rele o banco e desiste.
        oneTime.setStatus(OneTimeBackupStatus.CANCELADO);
        oneTimeBackupRepository.save(oneTime);

        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        return true;
    }

    public List<PendingScheduledBackupResponse> getPendingScheduledBackups() {
        LocalDateTime now = LocalDateTime.now();
        return oneTimeBackupRepository.findByStatusOrderByScheduledForAsc(OneTimeBackupStatus.PENDENTE).stream()
                .map(pending -> new PendingScheduledBackupResponse(
                        pending.getId(),
                        OneTimeBackupStatus.PENDENTE.name(),
                        formatRemainingTime(Duration.between(now, pending.getScheduledFor()).toSeconds()),
                        "/api/backup/scheduler/schedule/" + pending.getId() + "/cancel"
                ))
                .toList();
    }

    private String formatRemainingTime(long seconds) {
        if (seconds <= 0) {
            return "Executando agora";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes == 0) {
            return remainingSeconds + " segundos";
        }
        return minutes + " minutos e " + remainingSeconds + " segundos";
    }

    @Scheduled(fixedDelay = 300000)
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
