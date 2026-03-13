package com.backup_manager.domain.event;

import com.backup_manager.domain.model.BackupTask;
import lombok.Getter;

@Getter
public class BackupCompletedEvent {
    private final BackupTask task;
    private final long durationSeconds;

    public BackupCompletedEvent(BackupTask task, long durationSeconds) {
        this.task = task;
        this.durationSeconds = durationSeconds;
    }
}