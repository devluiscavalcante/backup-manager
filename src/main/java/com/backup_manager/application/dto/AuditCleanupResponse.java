package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AuditCleanupResponse {

    private final long deletedCount;
    private final int retentionDays;
    private final LocalDateTime cutoffDate;
    private final boolean automatic;
}
