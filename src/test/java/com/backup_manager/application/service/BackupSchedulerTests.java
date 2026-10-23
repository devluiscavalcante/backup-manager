package com.backup_manager.application.service;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.domain.event.ScheduledBackupFailedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.OneTimeBackupEntity;
import com.backup_manager.domain.model.OneTimeBackupStatus;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import com.backup_manager.infrastructure.persistence.OneTimeBackupRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                mock(ThreadPoolTaskScheduler.class), mock(BackupSchedulerProperties.class), publisher,
                mock(OneTimeBackupRepository.class));
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

    @Test
    void scheduledCronShouldNotWrapDispatchesInOneTransaction() throws NoSuchMethodException {
        // Uma transacao em volta do loop escondia as tarefas recem-criadas do worker @Async (issue #88).
        assertThat(BackupScheduler.class.getMethod("executeScheduledBackups")
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(BackupScheduler.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void scheduledCronShouldStartEveryEnabledConfiguration() {
        BackupService backupService = mock(BackupService.class);
        BackupSchedulerProperties properties = new BackupSchedulerProperties();
        properties.setEnabled(true);
        BackupSchedulerProperties.ScheduledBackupConfig nightly = new BackupSchedulerProperties.ScheduledBackupConfig();
        nightly.setName("nightly");
        nightly.setSources(List.of("source"));
        nightly.setDestinations(List.of("destination"));
        properties.setScheduledBackups(List.of(nightly));
        BackupScheduler scheduler = new BackupScheduler(backupService, mock(BackupRequestValidationService.class),
                mock(ThreadPoolTaskScheduler.class), properties, mock(ApplicationEventPublisher.class),
                mock(OneTimeBackupRepository.class));

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenReturn(7L);

        scheduler.executeScheduledBackups();

        verify(backupService).runBackup("source", "destination");
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
                publisher,
                mock(OneTimeBackupRepository.class)
        );
    }

    // ---------- execucoes unicas persistidas

    @Test
    void scheduleOneTimeBackupShouldPersistAndReturnDatabaseId() {
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        BackupScheduler scheduler = oneTimeScheduler(mock(BackupService.class), taskScheduler, repository);
        when(repository.save(any(OneTimeBackupEntity.class))).thenAnswer(invocation -> {
            OneTimeBackupEntity saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        Long id = scheduler.scheduleOneTimeBackup(request("source", "destination"), 30, "Noturno");

        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<OneTimeBackupEntity> saved = ArgumentCaptor.forClass(OneTimeBackupEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OneTimeBackupStatus.PENDENTE);
        assertThat(saved.getValue().getSources()).containsExactly("source");
        assertThat(saved.getValue().getScheduledFor()).isAfter(LocalDateTime.now().plusMinutes(29));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void pendingOneTimeBackupsShouldBeRescheduledOnStartupAndOverdueOnesRunNow() {
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        BackupScheduler scheduler = oneTimeScheduler(mock(BackupService.class), taskScheduler, repository);
        OneTimeBackupEntity overdue = oneTime(1L, LocalDateTime.now().minusHours(3));
        OneTimeBackupEntity future = oneTime(2L, LocalDateTime.now().plusHours(2));
        when(repository.findByStatusOrderByScheduledForAsc(OneTimeBackupStatus.PENDENTE))
                .thenReturn(List.of(overdue, future));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        Instant before = Instant.now();

        scheduler.reschedulePendingOneTimeBackups();

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), when.capture());
        assertThat(when.getAllValues().get(0)).isBetween(before, Instant.now());
        assertThat(when.getAllValues().get(1)).isAfter(Instant.now().plusSeconds(3600));
    }

    @Test
    void runOneTimeShouldMarkExecutedWhenBackupStarts() {
        BackupService backupService = mock(BackupService.class);
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        BackupScheduler scheduler = oneTimeScheduler(backupService, mock(ThreadPoolTaskScheduler.class), repository);
        OneTimeBackupEntity pending = oneTime(3L, LocalDateTime.now());
        when(repository.findById(3L)).thenReturn(Optional.of(pending));
        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenReturn(42L);

        scheduler.runOneTime(3L);

        assertThat(pending.getStatus()).isEqualTo(OneTimeBackupStatus.EXECUTADO);
        assertThat(pending.getExecutedAt()).isNotNull();
        verify(repository).save(pending);
    }

    @Test
    void runOneTimeShouldMarkFailureWhenNothingStarts() {
        BackupService backupService = mock(BackupService.class);
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        BackupScheduler scheduler = oneTimeScheduler(backupService, mock(ThreadPoolTaskScheduler.class), repository);
        OneTimeBackupEntity pending = oneTime(4L, LocalDateTime.now());
        when(repository.findById(4L)).thenReturn(Optional.of(pending));
        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination")).thenThrow(new IllegalStateException("disco"));

        scheduler.runOneTime(4L);

        assertThat(pending.getStatus()).isEqualTo(OneTimeBackupStatus.FALHA);
        assertThat(pending.getErrorMessage()).isNotBlank();
    }

    @Test
    void runOneTimeShouldSkipCancelledEntries() {
        BackupService backupService = mock(BackupService.class);
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        BackupScheduler scheduler = oneTimeScheduler(backupService, mock(ThreadPoolTaskScheduler.class), repository);
        OneTimeBackupEntity cancelled = oneTime(5L, LocalDateTime.now());
        cancelled.setStatus(OneTimeBackupStatus.CANCELADO);
        when(repository.findById(5L)).thenReturn(Optional.of(cancelled));

        scheduler.runOneTime(5L);

        verify(backupService, never()).runBackup(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void cancelShouldPersistCancellationAndOnlyAcceptPendingEntries() {
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        BackupScheduler scheduler = oneTimeScheduler(mock(BackupService.class), mock(ThreadPoolTaskScheduler.class), repository);
        OneTimeBackupEntity pending = oneTime(6L, LocalDateTime.now().plusHours(1));
        OneTimeBackupEntity executed = oneTime(7L, LocalDateTime.now().minusHours(1));
        executed.setStatus(OneTimeBackupStatus.EXECUTADO);
        when(repository.findById(6L)).thenReturn(Optional.of(pending));
        when(repository.findById(7L)).thenReturn(Optional.of(executed));

        assertThat(scheduler.cancelScheduledBackup(6L)).isTrue();
        assertThat(pending.getStatus()).isEqualTo(OneTimeBackupStatus.CANCELADO);
        assertThat(scheduler.cancelScheduledBackup(7L)).isFalse();
        assertThat(scheduler.cancelScheduledBackup(99L)).isFalse();
    }

    @Test
    void pendingListShouldComeFromDatabase() {
        OneTimeBackupRepository repository = mock(OneTimeBackupRepository.class);
        BackupScheduler scheduler = oneTimeScheduler(mock(BackupService.class), mock(ThreadPoolTaskScheduler.class), repository);
        when(repository.findByStatusOrderByScheduledForAsc(OneTimeBackupStatus.PENDENTE))
                .thenReturn(List.of(oneTime(8L, LocalDateTime.now().plusMinutes(10))));

        assertThat(scheduler.getPendingScheduledBackups())
                .singleElement()
                .satisfies(pending -> {
                    assertThat(pending.getTaskId()).isEqualTo(8L);
                    assertThat(pending.getStatus()).isEqualTo("PENDENTE");
                    assertThat(pending.getCancelUrl()).isEqualTo("/api/backup/scheduler/schedule/8/cancel");
                });
    }

    private BackupScheduler oneTimeScheduler(BackupService backupService,
                                             ThreadPoolTaskScheduler taskScheduler,
                                             OneTimeBackupRepository repository) {
        return new BackupScheduler(backupService, mock(BackupRequestValidationService.class), taskScheduler,
                mock(BackupSchedulerProperties.class), mock(ApplicationEventPublisher.class), repository);
    }

    private OneTimeBackupEntity oneTime(Long id, LocalDateTime scheduledFor) {
        OneTimeBackupEntity entity = new OneTimeBackupEntity();
        entity.setId(id);
        entity.setName("Backup Agendado");
        entity.setSources(List.of("source"));
        entity.setDestinations(List.of("destination"));
        entity.setScheduledFor(scheduledFor);
        return entity;
    }

    private BackupRequest request(String source, String destination) {
        BackupRequest request = new BackupRequest();
        request.setSources(List.of(source));
        request.setDestination(List.of(destination));
        return request;
    }
}
