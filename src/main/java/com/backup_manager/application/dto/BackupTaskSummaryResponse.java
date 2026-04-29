package com.backup_manager.application.dto;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BackupTaskSummaryResponse {

    private final Long id;
    private final String sourcePath;
    private final String destinationPath;
    private final Status status;
    private final String errorMessage;
    private final Long fileCount;
    private final BigDecimal totalSizeMB;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime pausedAt;
    private final String duration;

    public static BackupTaskSummaryResponse fromTask(BackupTask task) {
        String duration = "";
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            long seconds = Duration.between(task.getStartedAt(), task.getFinishedAt()).getSeconds();
            duration = String.format("%02d:%02d:%02d",
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }

        BigDecimal totalSize = Objects.requireNonNullElse(task.getTotalSizeMB(), BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new BackupTaskSummaryResponse(
                task.getId(),
                task.getSourcePath(),
                task.getDestinationPath(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getFileCount(),
                totalSize,
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getPausedAt(),
                duration
        );
    }
}
