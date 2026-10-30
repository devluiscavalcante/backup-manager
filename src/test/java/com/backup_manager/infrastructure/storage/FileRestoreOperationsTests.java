package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void fullRestoreShouldNotWriteThroughALinkAlreadyInsideTheTarget() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        Path outside = Files.createDirectories(tempDir.resolve("fora"));
        write(backup.resolve("documento.txt"));
        write(backup.resolve("docs").resolve("relatorio.txt"));
        write(backup.resolve("docs").resolve("sub").resolve("nota.txt"));
        Files.createDirectories(target);
        createSymbolicLinkOrSkip(target.resolve("docs"), outside);

        FileRestoreOperations.RestoreResult result =
                restoreOperations.restoreAll(backup, target, true, noOpCallback());

        assertThat(target.resolve("documento.txt")).exists();
        assertThat(outside.resolve("relatorio.txt")).doesNotExist();
        assertThat(outside.resolve("sub")).doesNotExist();
        assertThat(result.getFilesRestored()).isEqualTo(1);
        assertThat(result.getWarnings()).isEqualTo(1);
    }

    @Test
    void selectiveRestoreShouldNotWriteThroughALinkAlreadyInsideTheTarget() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path target = tempDir.resolve("restaurado");
        Path outside = Files.createDirectories(tempDir.resolve("fora"));
        write(backup.resolve("docs").resolve("relatorio.txt"));
        write(backup.resolve("fotos").resolve("ferias.jpg"));
        Files.createDirectories(target);
        createSymbolicLinkOrSkip(target.resolve("docs"), outside);
        createSymbolicLinkOrSkip(target.resolve("fotos"), outside);

        FileRestoreOperations.RestoreResult result = restoreOperations.restoreSelective(backup, target,
                List.of("docs/relatorio.txt", "fotos"), true, noOpCallback());

        assertThat(outside.resolve("relatorio.txt")).doesNotExist();
        assertThat(outside.resolve("ferias.jpg")).doesNotExist();
        assertThat(result.getFilesRestored()).isZero();
        assertThat(result.getWarnings()).isEqualTo(2);
    }

    @Test
    void restoreShouldWriteIntoTheRealTargetWhenTheTargetItselfIsALink() throws IOException {
        Path backup = tempDir.resolve("backup");
        Path realTarget = Files.createDirectories(tempDir.resolve("restaurado-real"));
        Path linkedTarget = tempDir.resolve("restaurado");
        write(backup.resolve("docs").resolve("relatorio.txt"));
        createSymbolicLinkOrSkip(linkedTarget, realTarget);

        FileRestoreOperations.RestoreResult result =
                restoreOperations.restoreAll(backup, linkedTarget, false, noOpCallback());

        assertThat(realTarget.resolve("docs").resolve("relatorio.txt")).exists();
        assertThat(result.getWarnings()).isZero();
    }

    @Test
    void isInsideTargetShouldAcceptMissingChildrenAndRejectLinksLeavingTheTarget() throws IOException {
        Path target = Files.createDirectories(tempDir.resolve("restaurado")).toRealPath();
        Path outside = Files.createDirectories(tempDir.resolve("fora"));
        createSymbolicLinkOrSkip(target.resolve("link"), outside);
        createSymbolicLinkOrSkip(target.resolve("quebrado"), tempDir.resolve("nao-existe"));

        assertThat(FileRestoreOperations.isInsideTarget(target, target)).isTrue();
        assertThat(FileRestoreOperations.isInsideTarget(target.resolve("novo").resolve("pasta"), target)).isTrue();
        assertThat(FileRestoreOperations.isInsideTarget(target.resolve("link"), target)).isFalse();
        assertThat(FileRestoreOperations.isInsideTarget(target.resolve("link").resolve("novo"), target)).isFalse();
        assertThat(FileRestoreOperations.isInsideTarget(target.resolve("quebrado").resolve("novo"), target)).isFalse();
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "Criacao de symlink indisponivel: " + e.getMessage());
        }
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
