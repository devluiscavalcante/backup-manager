package com.backup_manager.domain.event;

import com.backup_manager.domain.model.RestoreTask;
import lombok.Getter;

@Getter
public class RestoreCompletedEvent {
    private final RestoreTask task;
    private final long durationSeconds;

    public RestoreCompletedEvent(RestoreTask task, long durationSeconds) {
        this.task = task;
        this.durationSeconds = durationSeconds;
    }
}