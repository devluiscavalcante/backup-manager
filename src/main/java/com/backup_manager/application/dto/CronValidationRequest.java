package com.backup_manager.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CronValidationRequest {

    @NotBlank(message = "A expressao cron nao pode estar vazia")
    private String cronExpression;
}
