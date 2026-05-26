package com.backup_manager.application.dto;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BackupResponse {

    private String sourcePath;
    private String destinationPath;
    private Status status;
    private String errorMessage;
    private Long fileCount;
    private BigDecimal totalSizeMB;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime pausedAt;
    private String duration;


    public void setTotalSizeMB(BigDecimal sizeMB) {
        this.totalSizeMB = ResponseFormattingUtils.normalizeSize(sizeMB);
    }

    public static BackupResponse fromTask(BackupTask task) {
        return new BackupResponse(
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
