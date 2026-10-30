package com.backup_manager.application.service;

import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.domain.exception.ActiveBackupConflictException;
import com.backup_manager.domain.exception.BackupInitializationException;
import com.backup_manager.domain.event.BackupStartedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.service.BackupTaskManager;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.BackupSnapshots;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void runBackupShouldRejectPairAlreadyActiveUnderAnotherSpelling() throws IOException {
        BackupRepository repository = mock(BackupRepository.class);
        PathSecurityService pathSecurity = realPaths();
        BackupService service = spy(new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                mock(BackupTaskManager.class), mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                pathSecurity, Runnable::run, mock(BackupService.class)));
        BackupTask active = backupTask(Status.EM_ANDAMENTO, false);

        doNothing().when(service).validatePathAndDriveSpace("c:/dados/", "e:/backup");
        when(repository.findFirstByDestinationPathAndStatusInOrderByIdDesc(eq(REAL_DESTINATION.toString()), any()))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.runBackup("c:/dados/", "e:/backup"))
                .isInstanceOf(ActiveBackupConflictException.class)
                .extracting("conflictingTaskId").isEqualTo(7L);
        verify(repository, never()).save(any(BackupTask.class));
    }

    @Test
    void runBackupShouldRejectAnotherSourceWhileTheDestinationIsBusy() throws IOException {
        BackupRepository repository = mock(BackupRepository.class);
        BackupService service = spy(new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                mock(BackupTaskManager.class), mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                realPaths(), Runnable::run, mock(BackupService.class)));
        // Outra origem gravando no mesmo destino: as duas copias disputariam a mesma arvore.
        BackupTask otherSource = backupTask(Status.EM_ANDAMENTO, false);
        otherSource.setSourcePath("outra-origem");

        doNothing().when(service).validatePathAndDriveSpace("c:/dados/", "e:/backup");
        when(repository.findFirstByDestinationPathAndStatusInOrderByIdDesc(eq(REAL_DESTINATION.toString()), any()))
                .thenReturn(Optional.of(otherSource));

        assertThatThrownBy(() -> service.runBackup("c:/dados/", "e:/backup"))
                .isInstanceOf(ActiveBackupConflictException.class)
                .extracting("sourcePath").isEqualTo("outra-origem");
        verify(repository, never()).save(any(BackupTask.class));
    }

    @Test
    void runBackupShouldPersistRealPathsInsteadOfRawInput() throws IOException {
        BackupRepository repository = mock(BackupRepository.class);
        BackupService service = spy(new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                mock(BackupTaskManager.class), mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                realPaths(), Runnable::run, mock(BackupService.class)));

        doNothing().when(service).validatePathAndDriveSpace("c:/dados/", "e:/backup");
        when(repository.findFirstByDestinationPathAndStatusInOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(BackupTask.class))).thenAnswer(invocation -> {
            BackupTask task = invocation.getArgument(0);
            task.setId(9L);
            return task;
        });

        assertThat(service.runBackup("c:/dados/", "e:/backup")).isEqualTo(9L);

        ArgumentCaptor<BackupTask> saved = ArgumentCaptor.forClass(BackupTask.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourcePath()).isEqualTo(REAL_SOURCE.toString());
        assertThat(saved.getValue().getDestinationPath()).isEqualTo(REAL_DESTINATION.toString());
    }

    @Test
    void runBackupShouldFailTaskWhenExecutorQueueIsFull() throws IOException {
        BackupRepository repository = mock(BackupRepository.class);
        BackupService self = mock(BackupService.class);
        BackupTaskManager taskManager = mock(BackupTaskManager.class);
        BackupService service = spy(new BackupService(mock(BackupManager.class), repository, mock(ProgressEmitter.class),
                taskManager, mock(FileStorageOperations.class), mock(ApplicationEventPublisher.class),
                realPaths(), Runnable::run, self));
        BackupTask saved = backupTask(Status.EM_ANDAMENTO, false);
        when(taskManager.claimWorker(7L)).thenReturn(true);

        doNothing().when(service).validatePathAndDriveSpace("source", "destination");
        when(repository.findFirstByDestinationPathAndStatusInOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(BackupTask.class))).thenReturn(saved);
        when(repository.findById(7L)).thenReturn(Optional.of(saved));
        doThrow(new TaskRejectedException("fila cheia")).when(self).processBackupAsync(saved);

        assertThatThrownBy(() -> service.runBackup("source", "destination"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fila de backups cheia");

        assertThat(saved.getStatus()).isEqualTo(Status.FALHA);
        assertThat(saved.getFinishedAt()).isNotNull();
        verify(taskManager).releaseWorkerClaim(7L);
    }

    @Test
    void pauseShouldStopCopyAndReleaseThreadWithoutFinishingTask(@TempDir Path tempDir) throws IOException {
        Worker worker = new Worker(tempDir);
        BackupTask paused = backupTask(Status.PAUSADO, false);
        paused.setPaused(true);
        when(worker.taskManager.getTask(7L)).thenReturn(paused);
        when(worker.taskManager.releaseIfPaused(7L)).thenReturn(true);
        worker.copyRunsStoppingWhen(true);

        worker.service.processBackupAsync(worker.task);
        worker.assertCopyRunsMatched();

        verify(worker.storageOps, times(1)).copyDirectoryIncremental(any(), any(), any(), any(), any());
        verify(worker.repository, never()).save(any(BackupTask.class));
        verify(worker.taskManager, never()).unregisterTask(7L);
        verify(worker.progressEmitter, never()).sendControlEvent(eq("complete"), any(), any());
    }

    @Test
    void workerShouldKeepCopyingWhenResumedBeforeReleasingThread(@TempDir Path tempDir) throws IOException {
        Worker worker = new Worker(tempDir);
        BackupTask paused = backupTask(Status.PAUSADO, false);
        paused.setPaused(true);
        BackupTask running = backupTask(Status.EM_ANDAMENTO, false);
        // Gravar o caminho do snapshot consulta a tarefa; depois a 1a copia ve a pausa e a retomada chega
        // antes de o worker soltar a thread.
        when(worker.taskManager.getTask(7L)).thenReturn(running, paused, running);
        when(worker.taskManager.releaseIfPaused(7L)).thenReturn(false);
        when(worker.repository.findById(7L)).thenReturn(Optional.of(running));
        when(worker.repository.save(any(BackupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        worker.copyRunsStoppingWhen(true, false);

        worker.service.processBackupAsync(worker.task);
        worker.assertCopyRunsMatched();

        verify(worker.storageOps, times(2)).copyDirectoryIncremental(any(), any(), any(), any(), any());
        assertThat(running.getStatus()).isEqualTo(Status.CONCLUIDO);
        verify(worker.taskManager).unregisterTask(7L);
    }

    @Test
    void resumedWorkerShouldNotAnnounceANewBackup(@TempDir Path tempDir) throws IOException {
        Worker worker = new Worker(tempDir);
        when(worker.taskManager.getTask(7L)).thenReturn(backupTask(Status.EM_ANDAMENTO, false));
        when(worker.repository.findById(7L)).thenReturn(Optional.of(backupTask(Status.EM_ANDAMENTO, false)));
        when(worker.repository.save(any(BackupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        worker.copyRunsStoppingWhen(false);

        worker.service.resumeBackupAsync(worker.task);
        worker.assertCopyRunsMatched();

        verify(worker.eventPublisher, never()).publishEvent(any(BackupStartedEvent.class));
        verify(worker.progressEmitter, never()).sendControlEvent(eq("start"), any(), any());
        verify(worker.storageOps).copyDirectoryIncremental(any(), any(), any(), any(), any());
    }

    @Test
    void completedWorkerShouldPublishTheSnapshotAndRecordItsPath(@TempDir Path tempDir) throws IOException {
        Worker worker = new Worker(tempDir);
        BackupTask running = backupTask(Status.EM_ANDAMENTO, false);
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 28, 17, 0);
        worker.task.setStartedAt(startedAt);
        when(worker.taskManager.getTask(7L)).thenReturn(running);
        when(worker.repository.findById(7L)).thenReturn(Optional.of(running));
        when(worker.repository.save(any(BackupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        worker.copyRunsStoppingWhen(false);

        worker.service.processBackupAsync(worker.task);

        Path snapshot = BackupSnapshots.snapshotFor(tempDir.resolve("destination"), 7L, startedAt);
        assertThat(running.getStatus()).isEqualTo(Status.CONCLUIDO);
        assertThat(running.getSnapshotPath()).isEqualTo(snapshot.toString());
        assertThat(snapshot).isDirectory();
        assertThat(BackupSnapshots.partialOf(snapshot)).doesNotExist();
        // A copia grava no .partial e usa o snapshot anterior (aqui, a raiz vazia) como base dos hardlinks.
        verify(worker.storageOps).copyDirectoryIncremental(any(), eq(BackupSnapshots.partialOf(snapshot)),
                eq(tempDir.resolve("destination")), any(), any());
    }

    @Test
    void failedWorkerShouldDiscardThePartialSnapshot(@TempDir Path tempDir) throws IOException {
        Worker worker = new Worker(tempDir);
        BackupTask running = backupTask(Status.EM_ANDAMENTO, false);
        when(worker.taskManager.getTask(7L)).thenReturn(running);
        when(worker.repository.findById(7L)).thenReturn(Optional.of(running));
        when(worker.repository.save(any(BackupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(worker.storageOps.copyDirectoryIncremental(any(), any(), any(), any(), any()))
                .thenThrow(new IOException("disco cheio"));

        worker.service.processBackupAsync(worker.task);

        assertThat(running.getStatus()).isEqualTo(Status.FALHA);
        Path snapshots = BackupSnapshots.directory(tempDir.resolve("destination"));
        try (var entries = Files.list(snapshots)) {
            assertThat(entries).isEmpty();
        }
        // O log da execucao fica fora do snapshot descartado: os alertas da falha continuam consultaveis.
        try (var logs = Files.list(tempDir.resolve("destination").resolve(".backup-manager").resolve("logs"))) {
            assertThat(logs).singleElement().satisfies(log ->
                    assertThat(Files.readString(log)).contains("=== Backup #7 iniciado"));
        }
    }

    @Test
    void resumeShouldKeepTaskPausedWhenExecutorQueueIsFull() {
        BackupTaskManager taskManager = mock(BackupTaskManager.class);
        BackupService service = new BackupService(mock(BackupManager.class), mock(BackupRepository.class),
                mock(ProgressEmitter.class), taskManager, mock(FileStorageOperations.class),
                mock(ApplicationEventPublisher.class), mock(PathSecurityService.class), Runnable::run,
                mock(BackupService.class));
        when(taskManager.resumeTask(eq(7L), any())).thenThrow(new TaskRejectedException("fila cheia"));

        assertThat(service.resumeBackup(7L)).isFalse();

        verify(taskManager).pauseTask(7L);
    }

    // Worker com dependencias simuladas; a origem e o destino ficam numa pasta temporaria.
    private final class Worker {
        final BackupRepository repository = mock(BackupRepository.class);
        final BackupTaskManager taskManager = mock(BackupTaskManager.class);
        final BackupManager backupManager = mock(BackupManager.class);
        final FileStorageOperations storageOps = mock(FileStorageOperations.class);
        final ProgressEmitter progressEmitter = mock(ProgressEmitter.class);
        final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        final BackupService service = new BackupService(backupManager, repository, progressEmitter, taskManager,
                storageOps, eventPublisher, mock(PathSecurityService.class), Runnable::run, mock(BackupService.class));
        final BackupTask task = backupTask(Status.EM_ANDAMENTO, false);

        Worker(Path tempDir) {
            Path source = tempDir.resolve("source");
            task.setSourcePath(source.toString());
            task.setDestinationPath(tempDir.resolve("destination").toString());
            when(taskManager.activateQueuedTask(7L, task)).thenReturn(task);
            when(backupManager.validateSource(source.toString())).thenReturn(source.toFile());
            when(backupManager.scan(any())).thenReturn(new BackupManager.FolderScan(3, 0));
        }

        final List<Boolean> continueAnswers = new ArrayList<>();

        // Cada execucao da copia consulta shouldContinue uma vez, como faria no primeiro arquivo.
        void copyRunsStoppingWhen(Boolean... expectedStops) throws IOException {
            when(storageOps.copyDirectoryIncremental(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                FileStorageOperations.BackupProgressCallback callback = invocation.getArgument(4);
                continueAnswers.add(callback.shouldContinue());
                return 0;
            });
            expectedContinues = Arrays.stream(expectedStops).map(stop -> !stop).toList();
        }

        private List<Boolean> expectedContinues = List.of();

        void assertCopyRunsMatched() {
            assertThat(continueAnswers).isEqualTo(expectedContinues);
        }
    }

    private static final Path REAL_SOURCE = Path.of("real", "Dados");
    private static final Path REAL_DESTINATION = Path.of("real", "Backup");

    // Simula a resolucao para o caminho real: grafias diferentes da mesma pasta viram o mesmo Path.
    private PathSecurityService realPaths() {
        PathSecurityService pathSecurity = mock(PathSecurityService.class);
        when(pathSecurity.validateManagedPath(any(), eq("backup"))).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw.toLowerCase().contains("dados") || raw.equals("source") ? REAL_SOURCE : REAL_DESTINATION;
        });
        return pathSecurity;
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
