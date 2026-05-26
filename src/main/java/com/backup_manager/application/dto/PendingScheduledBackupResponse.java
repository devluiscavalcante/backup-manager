package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PendingScheduledBackupResponse {

    private final Long taskId;
    private final String status;
    private final String remainingTime;
    private final String cancelUrl;
}
