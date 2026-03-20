package com.backup_manager.domain.event;

import com.backup_manager.domain.model.RestoreTask;
import lombok.Getter;

@Getter
public class RestoreStartedEvent {
    private final RestoreTask task;

    public RestoreStartedEvent(RestoreTask task) {
        this.task = task;
    }
}