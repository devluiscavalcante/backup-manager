package com.backup_manager.application.dto;

import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.domain.model.RestoreType;
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
public class RestoreTaskResponse {

    private final Long id;
    private final Long backupTaskId;
    private final String targetPath;
    private final RestoreType restoreType;
    private final RestoreStatus status;
    private final Long fileCount;
    private final BigDecimal totalSizeMB;
    private final Long restoredFiles;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final String errorMessage;
    private final boolean selectiveRestore;
    private final String duration;

    public static RestoreTaskResponse fromTask(RestoreTask task) {
        String duration = "";
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            long seconds = Duration.between(task.getStartedAt(), task.getFinishedAt()).getSeconds();
            duration = String.format("%02d:%02d:%02d",
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }

        BigDecimal totalSize = Objects.requireNonNullElse(task.getTotalSizeMB(), BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new RestoreTaskResponse(
                task.getId(),
                task.getSourceBackup() != null ? task.getSourceBackup().getId() : null,
                task.getTargetPath(),
                task.getRestoreType(),
                task.getStatus(),
                task.getFileCount(),
                totalSize,
                task.getRestoredFiles(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getErrorMessage(),
                task.getRestoreType() == RestoreType.SELECTIVE,
                duration
        );
    }
}
