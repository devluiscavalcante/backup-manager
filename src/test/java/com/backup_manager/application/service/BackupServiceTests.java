package com.backup_manager.application.service;

import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.exception.BackupInitializationException;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.logging.BackupContext;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
                mock(BackupContext.class),
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
                mock(BackupContext.class),
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
