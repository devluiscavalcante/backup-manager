package com.backup_manager.domain.event;

import com.backup_manager.domain.model.BackupTask;
import lombok.Getter;

@Getter
public class BackupFailedEvent {
    private final BackupTask task;
    private final String errorMessage;

    public BackupFailedEvent(BackupTask task, String errorMessage) {
        this.task = task;
        this.errorMessage = errorMessage;
    }
}