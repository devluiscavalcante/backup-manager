package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BackupSnapshotsTests {

    @TempDir
    Path destination;

    @Test
    void snapshotsShouldLiveInTheMetadataDirectoryNamedByDateAndTask() {
        Path snapshot = BackupSnapshots.snapshotFor(destination, 42, LocalDateTime.of(2026, 10, 28, 17, 15, 3));

        assertThat(snapshot).isEqualTo(destination.resolve(BackupMetadata.DIRECTORY)
                .resolve(BackupSnapshots.DIRECTORY).resolve("20261028-171503-42"));
        assertThat(BackupSnapshots.partialOf(snapshot).getFileName()).hasToString("20261028-171503-42.partial");
        assertThat(BackupSnapshots.startedAt(snapshot)).isEqualTo(LocalDateTime.of(2026, 10, 28, 17, 15, 3));
        assertThat(BackupMetadata.isMetadata(snapshot, destination)).isTrue();
    }

    @Test
    void completedShouldListNewestFirstIgnoringPartialAndForeignFolders() throws IOException {
        Path older = Files.createDirectories(snapshot(1, 1));
        Path newer = Files.createDirectories(snapshot(2, 2));
        Files.createDirectories(BackupSnapshots.partialOf(snapshot(3, 3)));
        Files.createDirectories(BackupSnapshots.directory(destination).resolve("pasta-do-usuario"));

        assertThat(BackupSnapshots.completed(destination)).containsExactly(newer, older);
        assertThat(BackupSnapshots.referenceFor(destination)).isEqualTo(newer);
    }

    @Test
    void referenceShouldBeTheLegacyMirrorWhenThereIsNoSnapshotYet() throws IOException {
        // Destino do formato antigo: a primeira execucao usa o espelho da raiz como base dos hardlinks.
        Files.writeString(destination.resolve("antigo.txt"), "espelho");

        assertThat(BackupSnapshots.completed(destination)).isEmpty();
        assertThat(BackupSnapshots.referenceFor(destination)).isEqualTo(destination);
    }

    @Test
    void stalePartialsShouldBeRemovedExceptTheOneBeingResumed() throws IOException {
        Path stale = BackupSnapshots.partialOf(snapshot(1, 1));
        Path resumed = BackupSnapshots.partialOf(snapshot(2, 2));
        Files.createDirectories(stale.resolve("sub"));
        Files.writeString(stale.resolve("sub").resolve("a.txt"), "x");
        Files.createDirectories(resumed);
        Path completed = Files.createDirectories(snapshot(0, 0));

        BackupSnapshots.deleteStalePartials(destination, resumed);

        assertThat(stale).doesNotExist();
        assertThat(resumed).exists();
        assertThat(completed).exists();
    }

    @Test
    void publishShouldTurnThePartialIntoTheFinalSnapshot() throws IOException {
        Path snapshot = snapshot(5, 5);
        Path partial = BackupSnapshots.partialOf(snapshot);
        Files.createDirectories(partial);
        Files.writeString(partial.resolve("a.txt"), "x");

        BackupSnapshots.publish(partial, snapshot);

        assertThat(partial).doesNotExist();
        assertThat(snapshot.resolve("a.txt")).hasContent("x");
    }

    @Test
    void deletingASnapshotShouldKeepTheContentLinkedFromAnother() throws IOException {
        Path first = Files.createDirectories(snapshot(1, 1));
        Path second = Files.createDirectories(snapshot(2, 2));
        Files.writeString(first.resolve("a.txt"), "compartilhado");
        try {
            Files.createLink(second.resolve("a.txt"), first.resolve("a.txt"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("destino sem hardlinks");
        }

        BackupSnapshots.deleteTree(first);

        assertThat(first).doesNotExist();
        assertThat(second.resolve("a.txt")).hasContent("compartilhado");
    }

    private Path snapshot(long taskId, int day) {
        return BackupSnapshots.snapshotFor(destination, taskId, LocalDateTime.of(2026, 1, 1 + day, 10, 0));
    }
}
