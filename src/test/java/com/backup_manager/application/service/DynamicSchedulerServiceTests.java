package com.backup_manager.application.service;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicSchedulerServiceTests {

    @Test
    void shouldNotUpdateLastExecutionWhenNoBackupStarts() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        BackupScheduler backupScheduler = mock(BackupScheduler.class);
        DynamicSchedulerService service = createService(repository, backupScheduler);
        ScheduledBackupEntity config = scheduledBackup();

        when(backupScheduler.executeScheduledRun(any(), eq(config.getName())))
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

        when(backupScheduler.executeScheduledRun(any(), eq(config.getName())))
                .thenReturn(List.of(42L));
        when(repository.findById(config.getId())).thenReturn(Optional.of(config));

        service.executeScheduledBackup(config);

        assertThat(config.getLastExecution()).isNotNull();
        verify(repository).save(config);
    }

    @Test
    void shouldScheduleEnabledBackupsWhenApplicationIsReady() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        DynamicSchedulerService service = new DynamicSchedulerService(
                repository,
                mock(BackupScheduler.class),
                mock(BackupRequestValidationService.class),
                taskScheduler,
                new BackupSchedulerProperties()
        );
        ScheduledBackupEntity config = scheduledBackup();

        when(repository.findByEnabledTrue()).thenReturn(List.of(config));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        service.loadScheduledBackupsOnStartup();

        verify(taskScheduler).schedule(any(Runnable.class), argThat((Trigger trigger) ->
                trigger instanceof CronTrigger cron && cron.getExpression().equals(config.getCronExpression())));
    }

    @Test
    void shouldScheduleCronInConfiguredTimeZoneInsteadOfServerZone() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        BackupSchedulerProperties properties = new BackupSchedulerProperties();
        properties.setTimeZone("Asia/Tokyo");
        DynamicSchedulerService service = new DynamicSchedulerService(
                repository,
                mock(BackupScheduler.class),
                mock(BackupRequestValidationService.class),
                taskScheduler,
                properties
        );

        when(repository.findByEnabledTrue()).thenReturn(List.of(scheduledBackup()));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        service.refreshAllTasks();

        // 00:00 UTC = 09:00 em Toquio: o proximo "01:00" no fuso configurado e 16:00 UTC.
        SimpleTriggerContext context = new SimpleTriggerContext(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        verify(taskScheduler).schedule(any(Runnable.class), argThat((Trigger trigger) ->
                Instant.parse("2026-01-01T16:00:00Z").equals(trigger.nextExecution(context))));
    }

    @Test
    void shouldCancelPreviousSchedulesOnRefresh() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> previousFuture = mock(ScheduledFuture.class);
        DynamicSchedulerService service = new DynamicSchedulerService(
                repository,
                mock(BackupScheduler.class),
                mock(BackupRequestValidationService.class),
                taskScheduler,
                new BackupSchedulerProperties()
        );

        when(repository.findByEnabledTrue()).thenReturn(List.of(scheduledBackup()));
        doReturn(previousFuture).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        service.refreshAllTasks();
        service.refreshAllTasks();

        verify(previousFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldSkipInvalidConfigurationsWhenScheduling() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        BackupRequestValidationService validationService = mock(BackupRequestValidationService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        DynamicSchedulerService service = new DynamicSchedulerService(
                repository,
                mock(BackupScheduler.class),
                validationService,
                taskScheduler,
                new BackupSchedulerProperties()
        );
        ScheduledBackupEntity config = scheduledBackup();

        when(repository.findByEnabledTrue()).thenReturn(List.of(config));
        doThrow(new SecurityException("fora da allowlist"))
                .when(validationService).validatePathPolicy(config.getSources(), config.getDestinations());

        service.refreshAllTasks();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldKeepSchedulingRemainingConfigsWhenCronHasNoNextExecution() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        DynamicSchedulerService service = new DynamicSchedulerService(
                repository,
                mock(BackupScheduler.class),
                mock(BackupRequestValidationService.class),
                taskScheduler,
                new BackupSchedulerProperties()
        );
        ScheduledBackupEntity impossible = scheduledBackup();
        impossible.setCronExpression("0 0 0 30 2 *");
        ScheduledBackupEntity daily = scheduledBackup();
        daily.setId(11L);

        when(repository.findByEnabledTrue()).thenReturn(List.of(impossible, daily));
        doReturn(null).when(taskScheduler).schedule(any(Runnable.class), argThat((Trigger trigger) ->
                ((CronTrigger) trigger).getExpression().equals("0 0 0 30 2 *")));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), argThat((Trigger trigger) ->
                ((CronTrigger) trigger).getExpression().equals("0 0 1 * * *")));

        service.refreshAllTasks();

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    private DynamicSchedulerService createService(ScheduledBackupRepository repository,
                                                  BackupScheduler backupScheduler) {
        return new DynamicSchedulerService(
                repository,
                backupScheduler,
                mock(BackupRequestValidationService.class),
                mock(TaskScheduler.class),
                new BackupSchedulerProperties()
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
