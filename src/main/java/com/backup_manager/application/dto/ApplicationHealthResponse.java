package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApplicationHealthResponse {

    private final String status;
    private final String service;
    private final LocalDateTime timestamp;
    private final String version;
}
