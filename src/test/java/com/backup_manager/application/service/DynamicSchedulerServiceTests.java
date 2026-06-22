package com.backup_manager.application.service;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicSchedulerServiceTests {

    @Test
    void shouldNotUpdateLastExecutionWhenNoBackupStarts() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        BackupScheduler backupScheduler = mock(BackupScheduler.class);
        DynamicSchedulerService service = createService(repository, backupScheduler);
        ScheduledBackupEntity config = scheduledBackup();

        when(backupScheduler.executeBackupWithRequest(any(), eq(config.getName())))
                .thenReturn(List.of());

        service.executeScheduledBackup(config);

        verify(repository, never()).findById(config.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldUpdateLastExecutionWhenBackupStarts() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        BackupScheduler backupScheduler = mock(BackupScheduler.class);
        DynamicSchedulerService service = createService(repository, backupScheduler);
        ScheduledBackupEntity config = scheduledBackup();

        when(backupScheduler.executeBackupWithRequest(any(), eq(config.getName())))
                .thenReturn(List.of(42L));
        when(repository.findById(config.getId())).thenReturn(Optional.of(config));

        service.executeScheduledBackup(config);

        assertThat(config.getLastExecution()).isNotNull();
        verify(repository).save(config);
    }

    private DynamicSchedulerService createService(ScheduledBackupRepository repository,
                                                  BackupScheduler backupScheduler) {
        return new DynamicSchedulerService(
                repository,
                backupScheduler,
                mock(BackupRequestValidationService.class)
        );
    }

    private ScheduledBackupEntity scheduledBackup() {
        ScheduledBackupEntity config = new ScheduledBackupEntity();
        config.setId(10L);
        config.setName("daily");
        config.setSources(List.of("source"));
        config.setDestinations(List.of("destination"));
        config.setCronExpression("0 0 1 * * *");
        return config;
    }
}
