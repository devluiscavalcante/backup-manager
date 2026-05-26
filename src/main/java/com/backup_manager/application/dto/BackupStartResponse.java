package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class BackupStartResponse {

    private final String message;
    private final List<Long> taskIds;
    private final LocalDateTime timestamp;

    public static BackupStartResponse of(String message, List<Long> taskIds) {
        return new BackupStartResponse(message, taskIds, LocalDateTime.now());
    }
}
