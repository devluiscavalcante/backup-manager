package com.backup_manager.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Quantos snapshots cada destino guarda. Zero desliga o respectivo limite. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "backup.retention")
public class BackupRetentionProperties {

    @Min(0)
    private int maxSnapshots = 10;

    @Min(0)
    private int maxDays = 0;
}
