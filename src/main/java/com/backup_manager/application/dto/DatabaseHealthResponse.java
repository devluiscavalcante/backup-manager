package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DatabaseHealthResponse {

    private final String status;
    private final String database;
    private final String error;
    private final LocalDateTime timestamp;
}
