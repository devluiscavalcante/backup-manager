package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileStorageOperationsTests {

    private static final List<String> EXCLUSIONS = List.of("AppData", " Temp ", "node_modules", ".git", "$RECYCLE.BIN");

    private final FileStorageOperations storageOperations = new FileStorageOperations();

    @TempDir
    Path tempDir;

    @Test
    void shouldCopySourceEvenWhenItsPathContainsAnExcludedName() throws IOException {
        Path source = tempDir.resolve("Temp").resolve("origem");
        Path destination = tempDir.resolve("destino");
        write(source.resolve("documento.txt"));

        storageOperations.copyDirectoryIncremental(source, destination, EXCLUSIONS, noOpCallback());

        assertThat(destination.resolve("documento.txt")).exists();
    }

    @Test
    void shouldExcludeOnlyEntriesWhoseNameMatchesExactly() throws IOException {
        Path source = tempDir.resolve("origem");
        Path destination = tempDir.resolve("destino");
        write(source.resolve("node_modules").resolve("lib.js"));
        write(source.resolve("projeto").resolve(".git").resolve("HEAD"));
        write(source.resolve("projeto").resolve("TEMP").resolve("cache.tmp"));
        write(source.resolve("Templates").resolve("modelo.docx"));
        write(source.resolve("projeto").resolve(".github").resolve("ci.yml"));
        write(source.resolve("projeto").resolve(".gitignore"));

        storageOperations.copyDirectoryIncremental(source, destination, EXCLUSIONS, noOpCallback());

        assertThat(destination.resolve("node_modules")).doesNotExist();
        assertThat(destination.resolve("projeto").resolve(".git")).doesNotExist();
        assertThat(destination.resolve("projeto").resolve("TEMP")).doesNotExist();
        assertThat(destination.resolve("Templates").resolve("modelo.docx")).exists();
        assertThat(destination.resolve("projeto").resolve(".github").resolve("ci.yml")).exists();
        assertThat(destination.resolve("projeto").resolve(".gitignore")).exists();
    }

    @Test
    void shouldNotExcludeAnythingWhenExclusionListIsEmpty() throws IOException {
        Path source = tempDir.resolve("origem");
        Path destination = tempDir.resolve("destino");
        write(source.resolve("node_modules").resolve("lib.js"));

        storageOperations.copyDirectoryIncremental(source, destination, List.of(), noOpCallback());

        assertThat(destination.resolve("node_modules").resolve("lib.js")).exists();
    }

    @Test
    void shouldSkipUnreadableDirectoryAndKeepCopyingTheRest() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "Requer permissoes POSIX");

        Path source = tempDir.resolve("origem");
        Path destination = tempDir.resolve("destino");
        Path lockedDir = source.resolve("bloqueada");
        write(lockedDir.resolve("segredo.txt"));
        write(source.resolve("livre.txt"));

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(lockedDir);
        Files.setPosixFilePermissions(lockedDir, PosixFilePermissions.fromString("---------"));
        try {
            assumeTrue(!Files.isReadable(lockedDir), "Usuario atual ignora permissoes (root)");

            int warnings = storageOperations.copyDirectoryIncremental(source, destination, List.of(), noOpCallback());

            assertThat(warnings).isGreaterThanOrEqualTo(1);
            assertThat(destination.resolve("livre.txt")).exists();
            assertThat(destination.resolve("bloqueada").resolve("segredo.txt")).doesNotExist();
        } finally {
            Files.setPosixFilePermissions(lockedDir, originalPermissions);
        }
    }

    @Test
    void shouldFailWhenSourceRootIsInaccessible() {
        Path missingSource = tempDir.resolve("inexistente");

        assertThatThrownBy(() -> storageOperations.copyDirectoryIncremental(
                missingSource, tempDir.resolve("destino"), List.of(), noOpCallback()))
                .isInstanceOf(NoSuchFileException.class);
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "conteudo");
    }

    private FileStorageOperations.BackupProgressCallback noOpCallback() {
        return new FileStorageOperations.BackupProgressCallback() {
            @Override
            public void onFileProcessed(Path file, BasicFileAttributes attrs) {
            }

            @Override
            public void onWarning(String message, Path path) {
            }

            @Override
            public boolean shouldContinue() {
                return true;
            }
        };
    }
}
