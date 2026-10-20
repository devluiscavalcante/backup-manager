package com.backup_manager.infrastructure.config;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupCleanupRunnerTests {

    private final BackupRepository repository = mock(BackupRepository.class);
    private final RestoreRepository restoreRepository = mock(RestoreRepository.class);
    private final BackupCleanupRunner runner = new BackupCleanupRunner(repository, restoreRepository);

    @Test
    void marksInterruptedTasksAsFailed() {
        BackupTask running = task(1L, Status.EM_ANDAMENTO);
        BackupTask paused = task(2L, Status.PAUSADO);
        when(repository.findByStatusIn(List.of(Status.EM_ANDAMENTO, Status.PAUSADO)))
                .thenReturn(List.of(running, paused));

        runner.run();

        assertThat(List.of(running, paused)).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(Status.FALHA);
            assertThat(task.getFinishedAt()).isNotNull();
            assertThat(task.getErrorMessage()).isNotBlank();
        });
        verify(repository).saveAll(List.of(running, paused));
    }

    @Test
    void doesNothingWhenNoTaskWasInterrupted() {
        when(repository.findByStatusIn(any())).thenReturn(List.of());
        when(restoreRepository.findByStatus(RestoreStatus.EM_ANDAMENTO)).thenReturn(List.of());

        runner.run();

        verify(repository, never()).saveAll(any());
        verify(restoreRepository, never()).saveAll(any());
    }

    @Test
    void marksInterruptedRestoresAsFailed() {
        when(repository.findByStatusIn(any())).thenReturn(List.of());
        RestoreTask restore = new RestoreTask();
        restore.setId(3L);
        restore.setStatus(RestoreStatus.EM_ANDAMENTO);
        when(restoreRepository.findByStatus(RestoreStatus.EM_ANDAMENTO)).thenReturn(List.of(restore));

        runner.run();

        assertThat(restore.getStatus()).isEqualTo(RestoreStatus.FALHA);
        assertThat(restore.getFinishedAt()).isNotNull();
        assertThat(restore.getErrorMessage()).isNotBlank();
        verify(restoreRepository).saveAll(List.of(restore));
    }

    private static BackupTask task(Long id, Status status) {
        BackupTask task = new BackupTask();
        task.setId(id);
        task.setStatus(status);
        return task;
    }
}
