package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiErrorResponse {

    private final String error;
    private final Long taskId;
    private final LocalDateTime timestamp;

    public static ApiErrorResponse of(String error) {
        return new ApiErrorResponse(error, null, LocalDateTime.now());
    }

    public static ApiErrorResponse of(String error, Long taskId) {
        return new ApiErrorResponse(error, taskId, LocalDateTime.now());
    }
}
