package com.backup_manager.application.dto;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ScheduledBackupResponse {

    private final Long id;
    private final String name;
    private final List<String> sources;
    private final List<String> destinations;
    private final String cronExpression;
    private final boolean enabled;
    private final LocalDateTime lastExecution;
    private final LocalDateTime nextExecution;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static ScheduledBackupResponse fromEntity(ScheduledBackupEntity entity, LocalDateTime nextExecution) {
        return new ScheduledBackupResponse(
                entity.getId(),
                entity.getName(),
                entity.getSources(),
                entity.getDestinations(),
                entity.getCronExpression(),
                entity.isEnabled(),
                entity.getLastExecution(),
                nextExecution,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
