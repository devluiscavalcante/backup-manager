package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiErrorResponse {

    private final boolean success;
    private final int status;
    private final String error;
    private final String code;
    private final Object details;
    private final String path;
    private final Long taskId;
    private final LocalDateTime timestamp;

    public static ApiErrorResponse of(HttpStatus status, String error) {
        return new ApiErrorResponse(false, status.value(), error, null, null, null, null, LocalDateTime.now());
    }

    public static ApiErrorResponse of(HttpStatus status, String error, Long taskId) {
        return new ApiErrorResponse(false, status.value(), error, null, null, null, taskId, LocalDateTime.now());
    }

    public static ApiErrorResponse of(HttpStatus status,
                                      String error,
                                      String code,
                                      Object details,
                                      String path) {
        return new ApiErrorResponse(false, status.value(), error, code, details, path, null, LocalDateTime.now());
    }

    public static ApiErrorResponse of(HttpStatus status,
                                      String error,
                                      String code,
                                      Object details,
                                      String path,
                                      Long taskId) {
        return new ApiErrorResponse(false, status.value(), error, code, details, path, taskId, LocalDateTime.now());
    }
}
