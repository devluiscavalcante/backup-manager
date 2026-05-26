package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SchedulerHealthResponse {

    private final String status;
    private final LocalDateTime timestamp;
    private final boolean schedulerEnabled;
    private final String service;
    private final String error;
}
