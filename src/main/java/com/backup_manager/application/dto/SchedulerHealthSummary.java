package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SchedulerHealthSummary {

    private final boolean schedulerEnabled;
    private final String cronExpression;
    private final String timeZone;
    private final int totalConfigurations;
    private final int enabledConfigurations;
    private final int recentExecutions;

    public static SchedulerHealthSummary from(SchedulerStatus status) {
        return new SchedulerHealthSummary(
                status.isEnabled(),
                status.getCronExpression(),
                status.getTimeZone(),
                status.getTotalConfigurations(),
                status.getEnabledConfigurations(),
                status.getRecentExecutions()
        );
    }

    public static SchedulerHealthSummary down() {
        return new SchedulerHealthSummary(false, null, null, 0, 0, 0);
    }
}
