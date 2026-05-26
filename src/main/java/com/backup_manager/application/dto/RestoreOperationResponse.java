package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class RestoreOperationResponse {

    private final Long taskId;
    private final String status;
    private final Integer filesCount;
    private final String message;
    private final LocalDateTime timestamp;

    public static RestoreOperationResponse started(Long taskId, String message) {
        return new RestoreOperationResponse(taskId, "EM_ANDAMENTO", null, message, LocalDateTime.now());
    }

    public static RestoreOperationResponse selectiveStarted(Long taskId, int filesCount, String message) {
        return new RestoreOperationResponse(taskId, "EM_ANDAMENTO", filesCount, message, LocalDateTime.now());
    }

    public static RestoreOperationResponse completed(Long taskId, String status, String message) {
        return new RestoreOperationResponse(taskId, status, null, message, LocalDateTime.now());
    }
}
