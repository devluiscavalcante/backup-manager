package com.backup_manager.application.service;

import com.backup_manager.domain.event.BackupCompletedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.config.BackupRetentionProperties;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.BackupSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupRetentionServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);

    @TempDir
    Path destination;

    private final BackupRepository repository = mock(BackupRepository.class);

    @Test
    void shouldKeepOnlyTheNewestSnapshotsUpToTheLimit() throws IOException {
        List<Path> snapshots = createSnapshots(5);
        BackupTask expiredTask = new BackupTask();
        when(repository.findBySnapshotPathIn(any())).thenReturn(List.of(expiredTask));

        List<Path> removed = service(3, 0).apply(destination, snapshots.get(0), NOW);

        assertThat(removed).containsExactly(snapshots.get(3), snapshots.get(4));
        assertThat(BackupSnapshots.completed(destination)).containsExactlyElementsOf(snapshots.subList(0, 3));
        // A entrada continua no historico, marcada como sem snapshot.
        verify(repository).findBySnapshotPathIn(List.of(snapshots.get(3).toString(), snapshots.get(4).toString()));
        assertThat(expiredTask.getSnapshotRemovedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRemoveSnapshotsOlderThanTheMaxDaysButNeverTheNewest() throws IOException {
        List<Path> snapshots = createSnapshots(3);

        List<Path> removed = service(0, 2).apply(destination, snapshots.get(0), NOW.plusDays(30));

        assertThat(removed).containsExactly(snapshots.get(1), snapshots.get(2));
        assertThat(snapshots.get(0)).exists();
    }

    @Test
    void zeroLimitsShouldKeepEverything() throws IOException {
        List<Path> snapshots = createSnapshots(4);

        assertThat(service(0, 0).apply(destination, snapshots.get(0), NOW)).isEmpty();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void selectExpiredShouldNeverPickTheSnapshotJustWritten() {
        Path a = destination.resolve("20260103-100000-3");
        Path b = destination.resolve("20260102-100000-2");
        Path c = destination.resolve("20260101-100000-1");

        assertThat(BackupRetentionService.selectExpired(List.of(a, b, c), b, 1, 0, NOW)).containsExactly(c);
    }

    @Test
    void cleanupFailureShouldNotEscapeTheCompletedEvent() throws IOException {
        List<Path> snapshots = createSnapshots(2);
        BackupTask task = new BackupTask();
        task.setDestinationPath(destination.toString());
        task.setSnapshotPath(snapshots.get(0).toString());
        when(repository.findBySnapshotPathIn(any())).thenThrow(new IllegalStateException("db fora"));

        // Uma excecao aqui viraria FALHA num backup que ja terminou.
        assertThatCode(() -> service(1, 0).onBackupCompleted(new BackupCompletedEvent(task, 1)))
                .doesNotThrowAnyException();
    }

    // Do mais recente para o mais antigo, um por dia ate a vespera de NOW.
    private List<Path> createSnapshots(int count) throws IOException {
        List<Path> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Path snapshot = BackupSnapshots.snapshotFor(destination, 100 - i, NOW.minusDays(i + 1));
            Files.createDirectories(snapshot);
            Files.writeString(snapshot.resolve("a.txt"), "v" + i);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private BackupRetentionService service(int maxSnapshots, int maxDays) {
        BackupRetentionProperties properties = new BackupRetentionProperties();
        properties.setMaxSnapshots(maxSnapshots);
        properties.setMaxDays(maxDays);
        return new BackupRetentionService(properties, repository);
    }
}
