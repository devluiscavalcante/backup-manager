package com.backup_manager.application.service;

import com.backup_manager.application.dto.AuditCleanupResponse;
import com.backup_manager.infrastructure.config.AuditRetentionProperties;
import com.backup_manager.infrastructure.persistence.SecurityAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SecurityAuditRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditRetentionService.class);

    private final SecurityAuditEventRepository repository;
    private final AuditRetentionProperties properties;

    public SecurityAuditRetentionService(SecurityAuditEventRepository repository,
                                         AuditRetentionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public AuditCleanupResponse purgeExpiredEvents(boolean automatic) {
        if (!properties.isEnabled()) {
            logger.info("Expurgo de auditoria desabilitado por configuracao.");
            return new AuditCleanupResponse(0, properties.getMaxDays(), cutoffDate(), automatic);
        }

        LocalDateTime cutoffDate = cutoffDate();
        long deletedCount = repository.deleteByCreatedAtBefore(cutoffDate);

        logger.info("Expurgo de auditoria concluido. Removidos {} eventos anteriores a {}", deletedCount, cutoffDate);
        return new AuditCleanupResponse(deletedCount, properties.getMaxDays(), cutoffDate, automatic);
    }

    LocalDateTime cutoffDate() {
        return LocalDateTime.now().minusDays(properties.getMaxDays());
    }
}
