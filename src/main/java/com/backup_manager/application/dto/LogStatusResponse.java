package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LogStatusResponse {

    private final boolean logsAvailable;
    private final String[] availableLogs;
    private final String message;

    public static LogStatusResponse available(String... availableLogs) {
        return new LogStatusResponse(true, availableLogs, null);
    }

    public static LogStatusResponse unavailable(String message) {
        return new LogStatusResponse(false, new String[0], message);
    }
}
