package com.backup_manager.infrastructure.config;

import com.backup_manager.application.dto.AuditCleanupResponse;
import com.backup_manager.application.service.SecurityAuditRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditCleanupRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditCleanupRunner.class);

    private final SecurityAuditRetentionService retentionService;

    public SecurityAuditCleanupRunner(SecurityAuditRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Override
    public void run(String... args) {
        AuditCleanupResponse result = retentionService.purgeExpiredEvents(true);
        logger.info("Retencao de auditoria aplicada: {} dias, {} registros removidos.",
                result.getRetentionDays(), result.getDeletedCount());
    }
}
