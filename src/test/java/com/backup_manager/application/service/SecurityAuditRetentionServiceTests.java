package com.backup_manager.application.service;

import com.backup_manager.application.dto.AuditCleanupResponse;
import com.backup_manager.infrastructure.config.AuditRetentionProperties;
import com.backup_manager.infrastructure.persistence.SecurityAuditEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditRetentionServiceTests {

    private final SecurityAuditEventRepository repository = mock(SecurityAuditEventRepository.class);
    private final AuditRetentionProperties properties = new AuditRetentionProperties();
    private final SecurityAuditRetentionService service = new SecurityAuditRetentionService(repository, properties);

    @Test
    void purgeExpiredEventsShouldDeleteEventsOlderThanConfiguredWindow() {
        properties.setEnabled(true);
        properties.setMaxDays(30);
        when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(7L);

        AuditCleanupResponse result = service.purgeExpiredEvents(false);

        assertThat(result.getDeletedCount()).isEqualTo(7L);
        assertThat(result.getRetentionDays()).isEqualTo(30);
        assertThat(result.isAutomatic()).isFalse();
        verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void purgeExpiredEventsShouldSkipDeletionWhenDisabled() {
        properties.setEnabled(false);
        properties.setMaxDays(45);

        AuditCleanupResponse result = service.purgeExpiredEvents(true);

        assertThat(result.getDeletedCount()).isZero();
        assertThat(result.getRetentionDays()).isEqualTo(45);
        assertThat(result.isAutomatic()).isTrue();
    }
}
