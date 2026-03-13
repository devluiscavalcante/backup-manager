package com.backup_manager.domain.event;

import com.backup_manager.domain.model.BackupTask;
import lombok.Getter;

@Getter
public class BackupStartedEvent {
    private final BackupTask task;
    private final boolean isScheduled;

    public BackupStartedEvent(BackupTask task, boolean isScheduled) {
        this.task = task;
        this.isScheduled = isScheduled;
    }
}