package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CronValidationResponse {
    private boolean valid;
    private String description;
    private String errorMessage;
    private List<LocalDateTime> nextExecutions;
    private LocalDateTime timestamp;
}
