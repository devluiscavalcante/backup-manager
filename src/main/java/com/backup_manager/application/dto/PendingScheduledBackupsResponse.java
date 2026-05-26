package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PendingScheduledBackupsResponse {

    private final boolean success;
    private final int count;
    private final List<PendingScheduledBackupResponse> pendingTasks;
    private final String error;
    private final LocalDateTime timestamp;
}
