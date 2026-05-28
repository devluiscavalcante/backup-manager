package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class HealthStatusResponse {

    private final String status;
    private final String service;
    private final String version;
    private final String message;
    private final Object details;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static HealthStatusResponse of(String status, String service, String version, String message) {
        return of(status, service, version, message, null);
    }

    public static HealthStatusResponse of(String status,
                                          String service,
                                          String version,
                                          String message,
                                          Object details) {
        return new HealthStatusResponse(
                status,
                service,
                version,
                message,
                details,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }
}
