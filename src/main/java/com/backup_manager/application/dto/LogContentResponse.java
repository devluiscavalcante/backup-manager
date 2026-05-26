package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LogContentResponse {

    private final String logType;
    private final String content;
    private final String message;
    private final LocalDateTime timestamp;

    public static LogContentResponse content(String logType, String content) {
        return new LogContentResponse(logType, content, null, LocalDateTime.now());
    }

    public static LogContentResponse message(String logType, String message) {
        return new LogContentResponse(logType, null, message, LocalDateTime.now());
    }
}
