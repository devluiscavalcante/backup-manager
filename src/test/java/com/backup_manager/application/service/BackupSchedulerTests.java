package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.domain.event.ScheduledBackupFailedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void executeScheduledRunShouldNotifyWhenNothingStarts() {
        BackupService backupService = mock(BackupService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BackupScheduler scheduler = createScheduler(backupService, publisher);
        BackupRequest request = request("source", "destination");

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenThrow(new IllegalStateException("disk_unavailable"));

        assertThat(scheduler.executeScheduledRun(request, "nightly")).isEmpty();

        ArgumentCaptor<ScheduledBackupFailedEvent> event = ArgumentCaptor.forClass(ScheduledBackupFailedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().getBackupName()).isEqualTo("nightly");
        assertThat(event.getValue().getFailures()).containsExactly("source -> destination: disk_unavailable");
    }

    @Test
    void executeScheduledRunShouldNotifyWhenPreValidationRejectsRequest() {
        BackupRequestValidationService validation = mock(BackupRequestValidationService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BackupScheduler scheduler = new BackupScheduler(mock(BackupService.class), validation,
                mock(ThreadPoolTaskScheduler.class), mock(BackupSchedulerProperties.class), publisher);
        BackupRequest request = request("source", "destination");

        doThrow(new IllegalStateException("disco_indisponivel"))
                .when(validation).validateExecutableRequest(request.getSources(), request.getDestination());

        assertThat(scheduler.executeScheduledRun(request, "nightly")).isEmpty();

        ArgumentCaptor<ScheduledBackupFailedEvent> event = ArgumentCaptor.forClass(ScheduledBackupFailedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().getFailures()).containsExactly("disco_indisponivel");
    }

    @Test
    void executeScheduledRunShouldNotifyPartialFailuresButReturnStartedTasks() {
        BackupService backupService = mock(BackupService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BackupScheduler scheduler = createScheduler(backupService, publisher);
        BackupRequest request = new BackupRequest();
        request.setSources(List.of("a", "b"));
        request.setDestination(List.of("x", "y"));

        when(backupService.getActiveTask(anyString(), anyString())).thenReturn(Optional.empty());
        when(backupService.runBackup("a", "x")).thenReturn(1L);
        when(backupService.runBackup("b", "y")).thenThrow(new IllegalArgumentException("Origem nao encontrada"));

        assertThat(scheduler.executeScheduledRun(request, "nightly")).containsExactly(1L);

        ArgumentCaptor<ScheduledBackupFailedEvent> event = ArgumentCaptor.forClass(ScheduledBackupFailedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().getFailures()).containsExactly("b -> y: Origem nao encontrada");
    }

    @Test
    void executeScheduledRunShouldNotNotifyWhenEverythingStarts() {
        BackupService backupService = mock(BackupService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BackupScheduler scheduler = createScheduler(backupService, publisher);

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenReturn(42L);

        assertThat(scheduler.executeScheduledRun(request("source", "destination"), "nightly")).containsExactly(42L);
        verify(publisher, never()).publishEvent(any());
    }

    private BackupScheduler createScheduler(BackupService backupService) {
        return createScheduler(backupService, mock(ApplicationEventPublisher.class));
    }

    private BackupScheduler createScheduler(BackupService backupService, ApplicationEventPublisher publisher) {
        return new BackupScheduler(
                backupService,
                mock(BackupRequestValidationService.class),
                mock(ThreadPoolTaskScheduler.class),
                mock(BackupSchedulerProperties.class),
                publisher
        );
    }

    private BackupRequest request(String source, String destination) {
        BackupRequest request = new BackupRequest();
        request.setSources(List.of(source));
        request.setDestination(List.of(destination));
        return request;
    }
}
