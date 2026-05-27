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
    private final String requestId;
    private final LocalDateTime timestamp;

    public static HealthStatusResponse of(String status, String service, String version, String message) {
        return new HealthStatusResponse(
                status,
                service,
                version,
                message,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }
}
