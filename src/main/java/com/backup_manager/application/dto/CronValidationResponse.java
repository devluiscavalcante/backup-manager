package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
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
    private String requestId;
    private LocalDateTime timestamp;

    public static CronValidationResponse of(boolean valid,
                                            String description,
                                            String errorMessage,
                                            List<LocalDateTime> nextExecutions) {
        return new CronValidationResponse(
                valid,
                description,
                errorMessage,
                nextExecutions,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }
}
