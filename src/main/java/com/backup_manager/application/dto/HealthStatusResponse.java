package com.backup_manager.application.dto;

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
    private final LocalDateTime timestamp;
}
