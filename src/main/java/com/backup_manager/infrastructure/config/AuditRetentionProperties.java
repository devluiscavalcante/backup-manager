package com.backup_manager.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "audit.retention")
public class AuditRetentionProperties {

    private boolean enabled = true;

    @Min(1)
    private int maxDays = 90;
}
