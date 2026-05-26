package com.backup_manager.application.dto;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        return new BackupTaskSummaryResponse(
                task.getId(),
                task.getSourcePath(),
                task.getDestinationPath(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getFileCount(),
                ResponseFormattingUtils.normalizeSize(task.getTotalSizeMB()),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getPausedAt(),
                ResponseFormattingUtils.formatDuration(task.getStartedAt(), task.getFinishedAt())
        );
    }
}
