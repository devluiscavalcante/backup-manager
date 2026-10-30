package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BackupManifestTests {

    @TempDir
    Path tempDir;

    @Test
    void manifestShouldRecordEveryFileWithItsHashOutsideTheContent() throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        write(snapshot.resolve("a.txt"), "alfa");
        write(snapshot.resolve("sub").resolve("b.txt"), "beta");

        List<BackupManifest.Entry> entries = BackupManifest.write(snapshot, tempDir.resolve("sem-base"), true);

        assertThat(entries).extracting(BackupManifest.Entry::relativePath).containsExactlyInAnyOrder("a.txt", "sub/b.txt");
        Map<String, BackupManifest.Entry> read = BackupManifest.read(BackupManifest.of(snapshot));
        assertThat(read.get("a.txt").sha256()).isEqualTo(BackupManifest.sha256(snapshot.resolve("a.txt")));
        assertThat(read.get("sub/b.txt").size()).isEqualTo(4);
        // O manifesto fica na pasta de metadados: nao e restaurado nem entra no proprio manifesto.
        assertThat(BackupMetadata.isMetadata(BackupManifest.of(snapshot), snapshot)).isTrue();
        assertThat(read).doesNotContainKey(".backup-manager/" + BackupManifest.FILE_NAME);
    }

    @Test
    void verificationShouldDetectACorruptedFileInTheDestination() throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        write(snapshot.resolve("relatorio.txt"), "conteudo original");
        write(snapshot.resolve("intacto.txt"), "ok");
        BackupManifest.write(snapshot, tempDir.resolve("sem-base"), true);
        FileTime mtime = Files.getLastModifiedTime(snapshot.resolve("relatorio.txt"));

        // Bit rot: mesmo tamanho e mesma data, conteudo diferente.
        Files.writeString(snapshot.resolve("relatorio.txt"), "conteudo 0riginal");
        Files.setLastModifiedTime(snapshot.resolve("relatorio.txt"), mtime);

        BackupManifest.Verification verification = BackupManifest.verify(snapshot);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.changed()).containsExactly("relatorio.txt");
        assertThat(verification.checked()).isEqualTo(2);
    }

    @Test
    void verificationShouldReportMissingAndUnexpectedFiles() throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        write(snapshot.resolve("a.txt"), "a");
        write(snapshot.resolve("b.txt"), "b");
        BackupManifest.write(snapshot, tempDir.resolve("sem-base"), true);
        Files.delete(snapshot.resolve("a.txt"));
        write(snapshot.resolve("intruso.txt"), "x");

        BackupManifest.Verification verification = BackupManifest.verify(snapshot);

        assertThat(verification.missing()).containsExactly("a.txt");
        assertThat(verification.unexpected()).containsExactly("intruso.txt");
        assertThat(verification.changed()).isEmpty();
    }

    @Test
    void intactSnapshotShouldPassVerification() throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        write(snapshot.resolve("a.txt"), "a");
        BackupManifest.write(snapshot, tempDir.resolve("sem-base"), true);

        assertThat(BackupManifest.verify(snapshot).intact()).isTrue();
    }

    @Test
    void snapshotWithoutManifestShouldNotBeReportedAsIntact() throws IOException {
        Path snapshot = Files.createDirectories(tempDir.resolve("snapshot"));

        assertThat(BackupManifest.verify(snapshot).manifestFound()).isFalse();
        assertThat(BackupManifest.verify(snapshot).intact()).isFalse();
    }

    @Test
    void hardLinkedFileShouldInheritTheHashOfThePreviousManifest() throws IOException {
        Path previous = tempDir.resolve("anterior");
        write(previous.resolve("a.txt"), "inalterado");
        BackupManifest.write(previous, tempDir.resolve("sem-base"), true);
        // Hash marcado no manifesto anterior: herdado, o arquivo nao e relido.
        Path manifest = BackupManifest.of(previous);
        Files.writeString(manifest, Files.readString(manifest).replaceFirst("^[0-9a-f]{64}", "f".repeat(64)));
        Path current = Files.createDirectories(tempDir.resolve("atual"));
        try {
            Files.createLink(current.resolve("a.txt"), previous.resolve("a.txt"));
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("destino sem hardlinks");
        }

        Map<String, BackupManifest.Entry> written = toMap(BackupManifest.write(current, previous, true));

        assertThat(written.get("a.txt").sha256()).isEqualTo("f".repeat(64));
    }

    @Test
    void disabledHashShouldKeepOnlySizeAndDate() throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        write(snapshot.resolve("a.txt"), "a");

        List<BackupManifest.Entry> entries = BackupManifest.write(snapshot, tempDir.resolve("sem-base"), false);

        assertThat(entries).singleElement().extracting(BackupManifest.Entry::sha256).isEqualTo(BackupManifest.NO_HASH);
        assertThat(BackupManifest.verify(snapshot).intact()).isTrue();
    }

    private static Map<String, BackupManifest.Entry> toMap(List<BackupManifest.Entry> entries) {
        return entries.stream().collect(java.util.stream.Collectors.toMap(BackupManifest.Entry::relativePath, e -> e));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
