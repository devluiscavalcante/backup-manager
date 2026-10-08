package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
