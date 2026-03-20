package com.backup_manager.domain.event;

import com.backup_manager.domain.model.RestoreTask;
import lombok.Getter;

@Getter
public class RestoreFailedEvent {
    private final RestoreTask task;
    private final String errorMessage;

    public RestoreFailedEvent(RestoreTask task, String errorMessage) {
        this.task = task;
        this.errorMessage = errorMessage;
    }
}