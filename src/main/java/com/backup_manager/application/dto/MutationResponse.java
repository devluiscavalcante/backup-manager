package com.backup_manager.application.dto;

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
    private final LocalDateTime timestamp;

    public static <T> MutationResponse<T> success(T data, String message) {
        return new MutationResponse<>(true, data, message, null, LocalDateTime.now());
    }

    public static <T> MutationResponse<T> success(T data, String message, String details) {
        return new MutationResponse<>(true, data, message, details, LocalDateTime.now());
    }
}
