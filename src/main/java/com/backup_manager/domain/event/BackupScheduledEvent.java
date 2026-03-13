package com.backup_manager.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class BackupScheduledEvent {
    private final Long scheduledBackupId;
    private final String backupName;
    private final List<String> sources;
    private final List<String> destinations;
    private final LocalDateTime nextExecution;
    private final String cronExpression;

    public BackupScheduledEvent(Long scheduledBackupId, String backupName,
                                List<String> sources, List<String> destinations,
                                LocalDateTime nextExecution, String cronExpression) {
        this.scheduledBackupId = scheduledBackupId;
        this.backupName = backupName;
        this.sources = sources;
        this.destinations = destinations;
        this.nextExecution = nextExecution;
        this.cronExpression = cronExpression;
    }
}