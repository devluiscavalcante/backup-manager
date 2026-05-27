package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MutationResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final String details;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static <T> MutationResponse<T> success(T data, String message) {
        return new MutationResponse<>(true, data, message, null, RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static <T> MutationResponse<T> success(T data, String message, String details) {
        return new MutationResponse<>(true, data, message, details, RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }
}
