package com.backup_manager.domain.event;

import com.backup_manager.domain.model.BackupTask;
import lombok.Getter;

@Getter
public class BackupCancelledEvent {
    private final BackupTask task;

    public BackupCancelledEvent(BackupTask task) {
        this.task = task;
    }
}