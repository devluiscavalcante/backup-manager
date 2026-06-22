package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupSchedulerTests {

    @Test
    void executeBackupWithRequestShouldReturnStartedTaskIds() {
        BackupService backupService = mock(BackupService.class);
        BackupScheduler scheduler = createScheduler(backupService);
        BackupRequest request = request("source", "destination");

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenReturn(42L);

        assertThat(scheduler.executeBackupWithRequest(request, "test")).containsExactly(42L);
    }

    @Test
    void executeBackupWithRequestShouldReturnEmptyWhenAllPairsAreActive() {
        BackupService backupService = mock(BackupService.class);
        BackupScheduler scheduler = createScheduler(backupService);
        BackupRequest request = request("source", "destination");
        BackupTask activeTask = new BackupTask();
        activeTask.setId(7L);

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.of(activeTask));

        assertThat(scheduler.executeBackupWithRequest(request, "test")).isEmpty();
    }

    @Test
    void executeBackupWithRequestShouldPropagateFailureWhenNothingStarts() {
        BackupService backupService = mock(BackupService.class);
        BackupScheduler scheduler = createScheduler(backupService);
        BackupRequest request = request("source", "destination");
        IllegalStateException failure = new IllegalStateException("disk_unavailable");

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenThrow(failure);

        assertThatThrownBy(() -> scheduler.executeBackupWithRequest(request, "test"))
                .isSameAs(failure);
    }

    private BackupScheduler createScheduler(BackupService backupService) {
        return new BackupScheduler(
                backupService,
                mock(BackupRequestValidationService.class),
                mock(ThreadPoolTaskScheduler.class),
                mock(BackupSchedulerProperties.class)
        );
    }

    private BackupRequest request(String source, String destination) {
        BackupRequest request = new BackupRequest();
        request.setSources(List.of(source));
        request.setDestination(List.of(destination));
        return request;
    }
}
