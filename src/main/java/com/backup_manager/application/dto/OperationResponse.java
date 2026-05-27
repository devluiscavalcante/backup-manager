package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class OperationResponse {

    private final boolean success;
    private final String message;
    private final String error;
    private final String status;
    private final Long taskId;
    private final List<Long> taskIds;
    private final Integer filesCount;
    private final String backupName;
    private final LocalDateTime scheduledTime;
    private final String cancelUrl;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static OperationResponse success(String message) {
        return new OperationResponse(true, message, null, null, null, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse success(String message, Long taskId) {
        return new OperationResponse(true, message, null, null, taskId, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse backupStarted(String message, List<Long> taskIds) {
        return new OperationResponse(
                true,
                message,
                null,
                "EM_ANDAMENTO",
                null,
                taskIds,
                null,
                null,
                null,
                null,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }

    public static OperationResponse scheduled(String message,
                                              Long taskId,
                                              String backupName,
                                              LocalDateTime scheduledTime,
                                              String cancelUrl) {
        return new OperationResponse(
                true,
                message,
                null,
                "AGENDADO",
                taskId,
                null,
                null,
                backupName,
                scheduledTime,
                cancelUrl,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }

    public static OperationResponse namedSuccess(String message, String backupName) {
        return new OperationResponse(true, message, null, null, null, null, null, backupName, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse restoreStarted(Long taskId, String message) {
        return new OperationResponse(true, message, null, "EM_ANDAMENTO", taskId, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse selectiveRestoreStarted(Long taskId, int filesCount, String message) {
        return new OperationResponse(true, message, null, "EM_ANDAMENTO", taskId, null, filesCount, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse restoreCompleted(Long taskId, String status, String message) {
        return new OperationResponse(true, message, null, status, taskId, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse error(String error) {
        return new OperationResponse(false, null, error, null, null, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static OperationResponse error(String error, Long taskId) {
        return new OperationResponse(false, null, error, null, taskId, null, null, null, null, null,
                RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }
}
