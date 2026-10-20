package com.backup_manager.application.service;

import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.exception.BackupInitializationException;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackupServiceTests {

    @Test
    void runBackupShouldPropagateSecurityFailureInsteadOfReturningNull() {
        PathSecurityService pathSecurityService = mock(PathSecurityService.class);
        ProgressEmitter progressEmitter = mock(ProgressEmitter.class);
        BackupService service = createService(pathSecurityService, progressEmitter);
        SecurityException failure = new SecurityException("path_not_allowed");

        doThrow(failure).when(pathSecurityService).validateManagedPath("source", "backup");

        assertThatThrownBy(() -> service.runBackup("source", "destination"))
                .isSameAs(failure);

        verify(progressEmitter).sendError("Erro de Seguranca: path_not_allowed");
    }

    @Test
    void runBackupShouldWrapIoFailureInsteadOfReturningNull() throws IOException {
        ProgressEmitter progressEmitter = mock(ProgressEmitter.class);
        BackupService service = spy(createService(mock(PathSecurityService.class), progressEmitter));
        IOException failure = new IOException("disk_unavailable");

        doThrow(failure).when(service).validatePathAndDriveSpace("source", "destination");

        assertThatThrownBy(() -> service.runBackup("source", "destination"))
                .isInstanceOf(BackupInitializationException.class)
                .hasMessage("Falha ao preparar o backup.")
                .hasCause(failure);

        verify(progressEmitter).sendError("Falha ao preparar o backup.");
    }

    @Test
    void processBackupAsyncShouldSkipTaskCancelledWhileQueued() {
        BackupTaskManager taskManager = mock(BackupTaskManager.class);
        BackupManager backupManager = mock(BackupManager.class);
        FileStorageOperations storageOps = mock(FileStorageOperations.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ProgressEmitter progressEmitter = mock(ProgressEmitter.class);
        BackupService service = new BackupService(
                backupManager,
                mock(BackupRepository.class),
                progressEmitter,
                taskManager,
                storageOps,
                eventPublisher,
                mock(PathSecurityService.class),
                Runnable::run,
                mock(BackupService.class)
        );

        BackupTask queuedTask = backupTask(Status.EM_ANDAMENTO, false);
        BackupTask cancelledTask = backupTask(Status.CANCELADO, true);
        when(taskManager.activateQueuedTask(queuedTask.getId(), queuedTask)).thenReturn(cancelledTask);

        service.processBackupAsync(queuedTask);

        verify(taskManager).unregisterTask(queuedTask.getId());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(backupManager, storageOps, progressEmitter);
    }

    @Test
    void updateShouldPreserveCancellationThatWonTheRace() {
        BackupRepository repository = mock(BackupRepository.class);
        BackupTaskManager taskManager = mock(BackupTaskManager.class);
        BackupService service = new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                taskManager, mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                mock(PathSecurityService.class), Runnable::run, mock(BackupService.class));

        // Le "em andamento", mas o cancelamento grava antes: o save do worker e recusado pelo @Version.
        when(repository.findById(7L))
                .thenReturn(Optional.of(backupTask(Status.EM_ANDAMENTO, false)))
                .thenReturn(Optional.of(backupTask(Status.CANCELADO, true)));
        when(repository.save(any(BackupTask.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(BackupTask.class, 7L));

        Optional<BackupTask> result = service.updateUnlessCancelled(7L, task -> task.setStatus(Status.CONCLUIDO));

        assertThat(result).isEmpty();
        verify(repository, times(1)).save(any(BackupTask.class));
    }

    @Test
    void runBackupShouldFailTaskWhenExecutorQueueIsFull() throws IOException {
        BackupRepository repository = mock(BackupRepository.class);
        BackupService self = mock(BackupService.class);
        BackupService service = spy(new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                mock(BackupTaskManager.class), mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                mock(PathSecurityService.class), Runnable::run, self));
        BackupTask saved = backupTask(Status.EM_ANDAMENTO, false);

        doNothing().when(service).validatePathAndDriveSpace("source", "destination");
        when(repository.save(any(BackupTask.class))).thenReturn(saved);
        when(repository.findById(7L)).thenReturn(Optional.of(saved));
        doThrow(new TaskRejectedException("fila cheia")).when(self).processBackupAsync(saved);

        assertThatThrownBy(() -> service.runBackup("source", "destination"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fila de backups cheia");

        assertThat(saved.getStatus()).isEqualTo(Status.FALHA);
        assertThat(saved.getFinishedAt()).isNotNull();
    }

    private BackupTask backupTask(Status status, boolean cancelled) {
        BackupTask task = new BackupTask();
        task.setId(7L);
        task.setSourcePath("source");
        task.setDestinationPath("destination");
        task.setStatus(status);
        task.setCancelled(cancelled);
        return task;
    }

    private BackupService createService(PathSecurityService pathSecurityService,
                                        ProgressEmitter progressEmitter) {
        Executor directExecutor = Runnable::run;
        BackupService self = mock(BackupService.class);

        return new BackupService(
                mock(BackupManager.class),
                mock(BackupRepository.class),
                progressEmitter,
                mock(BackupTaskManager.class),
                mock(FileStorageOperations.class),
                mock(ApplicationEventPublisher.class),
                pathSecurityService,
                directExecutor,
                self
        );
    }
}
