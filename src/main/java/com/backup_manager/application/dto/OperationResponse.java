package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OperationResponse {

    private final boolean success;
    private final String message;
    private final String error;
    private final Long taskId;
    private final String backupName;
    private final LocalDateTime scheduledTime;
    private final String cancelUrl;
    private final LocalDateTime timestamp;

    public static OperationResponse success(String message) {
        return new OperationResponse(true, message, null, null, null, null, null, LocalDateTime.now());
    }

    public static OperationResponse success(String message, Long taskId) {
        return new OperationResponse(true, message, null, taskId, null, null, null, LocalDateTime.now());
    }

    public static OperationResponse success(String message,
                                            Long taskId,
                                            String backupName,
                                            LocalDateTime scheduledTime,
                                            String cancelUrl) {
        return new OperationResponse(
                true,
                message,
                null,
                taskId,
                backupName,
                scheduledTime,
                cancelUrl,
                LocalDateTime.now()
        );
    }

    public static OperationResponse success(String message, String backupName) {
        return new OperationResponse(true, message, null, null, backupName, null, null, LocalDateTime.now());
    }

    public static OperationResponse error(String error) {
        return new OperationResponse(false, null, error, null, null, null, null, LocalDateTime.now());
    }

    public static OperationResponse error(String error, Long taskId) {
        return new OperationResponse(false, null, error, taskId, null, null, null, LocalDateTime.now());
    }
}
