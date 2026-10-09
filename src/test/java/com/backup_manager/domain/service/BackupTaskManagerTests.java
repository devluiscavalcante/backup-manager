package com.backup_manager.domain.service;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupTaskManagerTests {

    private static final Long TASK_ID = 7L;

    private final BackupRepository repository = mock(BackupRepository.class);
    private final BackupTaskManager taskManager = new BackupTaskManager(repository, mock(ApplicationEventPublisher.class));

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

    private BackupTask task(Status status) {
        BackupTask task = new BackupTask();
        task.setId(TASK_ID);
        task.setSourcePath("source");
        task.setDestinationPath("destination");
        task.setStatus(status);
        return task;
    }
}
