package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SchedulerInfoResponse {

    private final String status;
    private final boolean enabled;
    private final String cronExpression;
    private final String timeZone;
    private final int totalConfigurations;
    private final int enabledConfigurations;
    private final int recentExecutions;
}
