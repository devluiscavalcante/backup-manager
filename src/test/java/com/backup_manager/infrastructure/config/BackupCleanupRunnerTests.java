package com.backup_manager.infrastructure.config;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
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
    private final BackupCleanupRunner runner = new BackupCleanupRunner(repository);

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

        runner.run();

        verify(repository, never()).saveAll(any());
    }

    private static BackupTask task(Long id, Status status) {
        BackupTask task = new BackupTask();
        task.setId(id);
        task.setStatus(status);
        return task;
    }
}
