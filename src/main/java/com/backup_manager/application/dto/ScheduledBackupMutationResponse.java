package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ScheduledBackupMutationResponse {

    private final boolean success;
    private final ScheduledBackupResponse config;
    private final String message;
    private final String error;
    private final String cronDescription;
    private final LocalDateTime timestamp;

    public static ScheduledBackupMutationResponse success(ScheduledBackupResponse config,
                                                          String message,
                                                          String cronDescription) {
        return new ScheduledBackupMutationResponse(
                true,
                config,
                message,
                null,
                cronDescription,
                LocalDateTime.now()
        );
    }

    public static ScheduledBackupMutationResponse success(ScheduledBackupResponse config, String message) {
        return new ScheduledBackupMutationResponse(
                true,
                config,
                message,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ScheduledBackupMutationResponse error(String error) {
        return new ScheduledBackupMutationResponse(
                false,
                null,
                null,
                error,
                null,
                LocalDateTime.now()
        );
    }

    public static ScheduledBackupMutationResponse error(String error, String cronDescription) {
        return new ScheduledBackupMutationResponse(
                false,
                null,
                null,
                error,
                cronDescription,
                LocalDateTime.now()
        );
    }
}
