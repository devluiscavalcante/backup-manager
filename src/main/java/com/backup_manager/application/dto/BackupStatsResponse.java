package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BackupStatsResponse {
    private Long totalBackups;
    private Long completedBackups;
    private Long failedBackups;
    private Long cancelledBackups;
    private Long inProgressBackups;
    private Long pausedBackups;
    private BigDecimal successRate;
    private BigDecimal totalSizeMB;
    private BigDecimal avgDurationSeconds;
}