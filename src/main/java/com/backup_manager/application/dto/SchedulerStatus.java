package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SchedulerStatus {

    private boolean enabled;
    private String cronExpression;
    private String timeZone;
    private int totalConfigurations;
    private int enabledConfigurations;
    private int recentExecutions;
}
