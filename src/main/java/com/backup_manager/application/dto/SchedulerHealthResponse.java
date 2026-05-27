package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
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
    private final String requestId;

    public static SchedulerHealthResponse of(String status,
                                             boolean schedulerEnabled,
                                             String service,
                                             String error) {
        return new SchedulerHealthResponse(
                status,
                LocalDateTime.now(),
                schedulerEnabled,
                service,
                error,
                RequestTracingContext.currentRequestId()
        );
    }
}
