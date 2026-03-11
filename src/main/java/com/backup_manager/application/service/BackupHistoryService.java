package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.dto.BackupStatsResponse;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BackupHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(BackupHistoryService.class);

    private final BackupRepository backupRepository;

    public BackupHistoryService(BackupRepository backupRepository) {
        this.backupRepository = backupRepository;
    }

    public Page<BackupResponse> searchHistory(
            Status status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        logger.debug("Buscando histórico: status={}, startDate={}, endDate={}, page={}",
                status, startDate, endDate, pageable.getPageNumber());

        Page<BackupTask> tasks;

        if (status != null && startDate != null && endDate != null) {
            tasks = backupRepository.findByStatusAndStartedAtBetween(
                    status, startDate, endDate, pageable
            );
        } else if (status != null) {
            tasks = backupRepository.findByStatus(status, pageable);
        } else if (startDate != null && endDate != null) {
            tasks = backupRepository.findByStartedAtBetween(
                    startDate, endDate, pageable
            );
        } else {
            tasks = backupRepository.findAllOrderByStartedAtDesc(pageable);
        }

        logger.info("Encontrados {} backups (total: {})",
                tasks.getNumberOfElements(), tasks.getTotalElements());

        return tasks.map(this::convertToResponse);
    }

    public BackupStatsResponse getStatistics() {
        logger.debug("Calculando estatísticas de backups");

        long total = backupRepository.count();
        long completed = backupRepository.countByStatus(Status.CONCLUIDO);
        long failed = backupRepository.countByStatus(Status.FALHA);
        long cancelled = backupRepository.countByStatus(Status.CANCELADO);
        long inProgress = backupRepository.countByStatus(Status.EM_ANDAMENTO);
        long paused = backupRepository.countByStatus(Status.PAUSADO);

        BigDecimal successRate = total > 0
                ? BigDecimal.valueOf((completed * 100.0) / total)
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalSizeMB = backupRepository.sumSizeByStatus(Status.CONCLUIDO);

        List<BackupTask> completedTasks = backupRepository.findByStatus(Status.CONCLUIDO);
        BigDecimal avgDuration = calculateAverageDuration(completedTasks);

        logger.info("Estatísticas: total={}, concluídos={}, taxa de sucesso={}%",
                total, completed, successRate);

        return new BackupStatsResponse(
                total,
                completed,
                failed,
                cancelled,
                inProgress,
                paused,
                successRate,
                totalSizeMB,
                avgDuration
        );
    }

    public List<BackupResponse> getRecentBackups(int limit) {
        logger.debug("Buscando {} backups mais recentes", limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<BackupTask> tasks = backupRepository.findTopNByOrderByStartedAtDesc(pageable);

        logger.info("Encontrados {} backups recentes", tasks.size());

        return tasks.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private BackupResponse convertToResponse(BackupTask task) {
        String duration = "";
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            long seconds = Duration.between(task.getStartedAt(), task.getFinishedAt()).getSeconds();
            duration = String.format("%02d:%02d:%02d",
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }

        return new BackupResponse(
                task.getSourcePath(),
                task.getDestinationPath(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getFileCount(),
                task.getTotalSizeMB(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getPausedAt(),
                duration
        );
    }

    private BigDecimal calculateAverageDuration(List<BackupTask> tasks) {
        if (tasks.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long totalSeconds = tasks.stream()
                .filter(t -> t.getStartedAt() != null && t.getFinishedAt() != null)
                .mapToLong(t -> Duration.between(t.getStartedAt(), t.getFinishedAt()).getSeconds())
                .sum();

        long validTasksCount = tasks.stream()
                .filter(t -> t.getStartedAt() != null && t.getFinishedAt() != null)
                .count();

        if (validTasksCount == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf((double) totalSeconds / validTasksCount)
                .setScale(2, RoundingMode.HALF_UP);
    }
}