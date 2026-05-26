package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BackupConflictResponse {

    private final String error;
    private final String source;
    private final String destination;
    private final Long taskId;
    private final LocalDateTime timestamp;

    public static BackupConflictResponse of(String error, String source, String destination, Long taskId) {
        return new BackupConflictResponse(error, source, destination, taskId, LocalDateTime.now());
    }
}
