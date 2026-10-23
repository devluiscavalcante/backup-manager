package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileRestoreOperationsTests {

    private final FileRestoreOperations restoreOperations = new FileRestoreOperations();

    @TempDir
    Path tempDir;

    @Test
    void fullRestoreShouldSkipBackupWarningsLogAtRoot() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        write(backup.resolve("documento.txt"));
        write(backup.resolve(FileStorageOperations.WARNINGS_LOG));
        write(backup.resolve("logs").resolve(FileStorageOperations.WARNINGS_LOG));

        FileRestoreOperations.RestoreResult result =
                restoreOperations.restoreAll(backup, target, false, noOpCallback());

        assertThat(target.resolve("documento.txt")).exists();
        assertThat(target.resolve(FileStorageOperations.WARNINGS_LOG)).doesNotExist();
        // So o log gerado pelo backup e ignorado: um warnings.log do usuario em subpasta e restaurado.
        assertThat(target.resolve("logs").resolve(FileStorageOperations.WARNINGS_LOG)).exists();
        assertThat(result.getFilesRestored()).isEqualTo(2);
        assertThat(result.getWarnings()).isZero();
    }

    @Test
    void selectiveRestoreShouldSkipBackupWarningsLogAtRoot() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        write(backup.resolve("documento.txt"));
        write(backup.resolve(FileStorageOperations.WARNINGS_LOG));

        restoreOperations.restoreSelective(backup, target,
                List.of("documento.txt", FileStorageOperations.WARNINGS_LOG), false, noOpCallback());

        assertThat(target.resolve("documento.txt")).exists();
        assertThat(target.resolve(FileStorageOperations.WARNINGS_LOG)).doesNotExist();
    }

    @Test
    void fullRestoreShouldRestoreTheUsersRootWarningsLogAndSkipTheMetadataDirectory() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        write(backup.resolve("documento.txt"));
        Files.createDirectories(backup);
        Files.writeString(backup.resolve(FileStorageOperations.WARNINGS_LOG), "arquivo do usuario");
        write(BackupMetadata.warningsLog(backup));

        FileRestoreOperations.RestoreResult result =
                restoreOperations.restoreAll(backup, target, false, noOpCallback());

        assertThat(target.resolve(FileStorageOperations.WARNINGS_LOG)).hasContent("arquivo do usuario");
        assertThat(target.resolve(BackupMetadata.DIRECTORY)).doesNotExist();
        assertThat(result.getFilesRestored()).isEqualTo(2);
    }

    @Test
    void selectiveRestoreShouldSkipTheMetadataDirectory() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        write(backup.resolve("documento.txt"));
        write(BackupMetadata.warningsLog(backup));

        restoreOperations.restoreSelective(backup, target,
                List.of("documento.txt", BackupMetadata.DIRECTORY), false, noOpCallback());

        assertThat(target.resolve("documento.txt")).exists();
        assertThat(target.resolve(BackupMetadata.DIRECTORY)).doesNotExist();
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "conteudo");
    }

    private FileRestoreOperations.RestoreProgressCallback noOpCallback() {
        return new FileRestoreOperations.RestoreProgressCallback() {
            @Override
            public void onFileRestored(Path file, BasicFileAttributes attrs) {
            }

            @Override
            public void onWarning(String message, Path path) {
            }

            @Override
            public boolean shouldContinue() {
                return true;
            }

            @Override
            public void onProgress(int processed, int total) {
            }
        };
    }
}
