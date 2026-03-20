package com.backup_manager.domain.event;

import com.backup_manager.domain.model.RestoreTask;
import lombok.Getter;

@Getter
public class RestoreCancelledEvent {
    private final RestoreTask task;

    public RestoreCancelledEvent(RestoreTask task) {
        this.task = task;
    }
}