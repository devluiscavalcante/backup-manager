package com.backup_manager.domain.service;

import com.backup_manager.domain.event.BackupCancelledEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupTaskManagerTests {

    private static final Long TASK_ID = 7L;

    private final BackupRepository repository = mock(BackupRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BackupTaskManager taskManager = new BackupTaskManager(repository, eventPublisher);

    @BeforeEach
    void returnSavedEntity() {
        when(repository.saveAndFlush(any(BackupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cancelShouldReevaluateAfterConcurrentFinalizationInsteadOfOverwritingIt() {
        // 1a leitura: em andamento; o worker conclui antes do save do cancelamento (versao mudou).
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(task(Status.EM_ANDAMENTO)))
                .thenReturn(Optional.of(task(Status.CONCLUIDO)));
        when(repository.saveAndFlush(any(BackupTask.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(BackupTask.class, TASK_ID));

        assertThat(taskManager.cancelTask(TASK_ID)).isFalse();

        verify(repository, times(1)).saveAndFlush(any(BackupTask.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelShouldRetryWhenConcurrentPauseChangedTheTask() {
        BackupTask paused = task(Status.PAUSADO);
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(task(Status.EM_ANDAMENTO)))
                .thenReturn(Optional.of(paused));
        when(repository.saveAndFlush(any(BackupTask.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(BackupTask.class, TASK_ID))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(taskManager.cancelTask(TASK_ID)).isTrue();

        assertThat(paused.getStatus()).isEqualTo(Status.CANCELADO);
        assertThat(paused.isCancelled()).isTrue();
        verify(eventPublisher).publishEvent(any(BackupCancelledEvent.class));
    }

    @Test
    void pauseShouldRejectTaskThatIsNotRunning() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task(Status.CONCLUIDO)));

        assertThat(taskManager.pauseTask(TASK_ID)).isFalse();
        verify(repository, never()).saveAndFlush(any(BackupTask.class));
    }

    @Test
    void shouldReturnPersistedCancellationWhenTaskLeavesTheQueue() {
        BackupTask queuedTask = task(Status.EM_ANDAMENTO);
        BackupTask cancelledTask = task(Status.CANCELADO);
        cancelledTask.setCancelled(true);
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(cancelledTask));

        BackupTask activeTask = taskManager.activateQueuedTask(TASK_ID, queuedTask);

        assertThat(activeTask.isCancelled()).isTrue();
        assertThat(taskManager.getTask(TASK_ID)).isSameAs(cancelledTask);
    }

    @Test
    void shouldReturnPersistedPauseWhenTaskLeavesTheQueue() {
        BackupTask queuedTask = task(Status.EM_ANDAMENTO);
        BackupTask pausedTask = task(Status.PAUSADO);
        pausedTask.setPaused(true);
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask));

        BackupTask activeTask = taskManager.activateQueuedTask(TASK_ID, queuedTask);

        assertThat(activeTask.isPaused()).isTrue();
    }

    @Test
    void shouldKeepInMemoryChangeMadeWhileReloadingFromDatabase() {
        BackupTask queuedTask = task(Status.EM_ANDAMENTO);
        BackupTask staleSnapshot = task(Status.EM_ANDAMENTO);
        AtomicBoolean firstLookup = new AtomicBoolean(true);

        // Na primeira leitura (feita por activateQueuedTask) um pause concorrente acontece antes
        // da releitura retornar um snapshot ainda "em andamento".
        when(repository.findById(TASK_ID)).thenAnswer(invocation -> {
            if (firstLookup.getAndSet(false)) {
                taskManager.pauseTask(TASK_ID);
                return Optional.of(staleSnapshot);
            }
            return Optional.of(task(Status.EM_ANDAMENTO));
        });

        BackupTask activeTask = taskManager.activateQueuedTask(TASK_ID, queuedTask);

        assertThat(activeTask.isPaused()).isTrue();
        assertThat(activeTask.getStatus()).isEqualTo(Status.PAUSADO);
    }

    @Test
    void resumeShouldDispatchWorkerWhenPausedTaskReleasedItsThread() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask()));
        AtomicReference<BackupTask> dispatched = new AtomicReference<>();

        assertThat(taskManager.resumeTask(TASK_ID, dispatched::set)).isTrue();

        assertThat(dispatched.get()).isNotNull();
        assertThat(dispatched.get().getStatus()).isEqualTo(Status.EM_ANDAMENTO);
        assertThat(dispatched.get().isPaused()).isFalse();
    }

    @Test
    void resumeShouldNotDispatchWhileWorkerIsStillAttached() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task(Status.EM_ANDAMENTO)));
        taskManager.claimWorker(TASK_ID);
        taskManager.activateQueuedTask(TASK_ID, task(Status.EM_ANDAMENTO));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask()));
        AtomicBoolean dispatched = new AtomicBoolean(false);

        assertThat(taskManager.resumeTask(TASK_ID, task -> dispatched.set(true))).isTrue();

        assertThat(dispatched).isFalse();
        assertThat(taskManager.getTask(TASK_ID).isPaused()).isFalse();
    }

    @Test
    void resumeShouldNotDispatchWhilePausedTaskIsStillQueued() {
        // O worker foi despachado mas ainda nao saiu da fila, entao a tarefa nao esta em runningTasks.
        assertThat(taskManager.claimWorker(TASK_ID)).isTrue();
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask()));
        AtomicBoolean dispatched = new AtomicBoolean(false);

        assertThat(taskManager.resumeTask(TASK_ID, task -> dispatched.set(true))).isTrue();

        assertThat(dispatched).isFalse();
    }

    @Test
    void claimShouldFailWhenResumeAlreadyDispatchedWorker() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask()));
        taskManager.resumeTask(TASK_ID, task -> { });

        assertThat(taskManager.claimWorker(TASK_ID)).isFalse();
    }

    @Test
    void rejectedResumeDispatchShouldNotLeaveWorkerClaimed() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pausedTask()));

        assertThatThrownBy(() -> taskManager.resumeTask(TASK_ID, task -> {
            throw new IllegalStateException("fila cheia");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(taskManager.claimWorker(TASK_ID)).isTrue();
    }

    @Test
    void resumeShouldNotDispatchWhenTaskIsNotPaused() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task(Status.CONCLUIDO)));
        AtomicBoolean dispatched = new AtomicBoolean(false);

        assertThat(taskManager.resumeTask(TASK_ID, task -> dispatched.set(true))).isFalse();

        assertThat(dispatched).isFalse();
    }

    @Test
    void releaseShouldDetachWorkerWhileTaskStaysPaused() {
        BackupTask paused = pausedTask();
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task(Status.EM_ANDAMENTO)));
        taskManager.claimWorker(TASK_ID);
        taskManager.activateQueuedTask(TASK_ID, task(Status.EM_ANDAMENTO));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(paused));

        assertThat(taskManager.releaseIfPaused(TASK_ID)).isTrue();

        // Fora da memoria, a consulta cai no banco; uma retomada agora precisa despachar outro worker.
        AtomicBoolean dispatched = new AtomicBoolean(false);
        taskManager.resumeTask(TASK_ID, task -> dispatched.set(true));
        assertThat(dispatched).isTrue();
    }

    @Test
    void releaseShouldKeepWorkerWhenTaskWasResumedMeanwhile() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task(Status.EM_ANDAMENTO)));
        BackupTask staleInMemory = pausedTask();
        taskManager.activateQueuedTask(TASK_ID, staleInMemory);
        BackupTask resumed = task(Status.EM_ANDAMENTO);
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(resumed));

        assertThat(taskManager.releaseIfPaused(TASK_ID)).isFalse();

        // O worker volta a copiar lendo o estado retomado, nao o snapshot pausado.
        assertThat(taskManager.getTask(TASK_ID)).isSameAs(resumed);
    }

    private BackupTask pausedTask() {
        BackupTask task = task(Status.PAUSADO);
        task.setPaused(true);
        return task;
    }

    private BackupTask task(Status status) {
        BackupTask task = new BackupTask();
        task.setId(TASK_ID);
        task.setSourcePath("source");
        task.setDestinationPath("destination");
        task.setStatus(status);
        return task;
    }
}
