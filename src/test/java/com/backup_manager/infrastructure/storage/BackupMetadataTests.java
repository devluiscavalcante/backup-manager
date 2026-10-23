package com.backup_manager.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackupMetadataTests {

    @TempDir
    Path backupRoot;

    @Test
    void prepareShouldCreateTheLogInsideTheMetadataDirectory() throws IOException {
        Path log = BackupMetadata.prepareWarningsLog(backupRoot);

        assertThat(log).isEqualTo(backupRoot.resolve(".backup-manager").resolve("warnings.log"));
        assertThat(log.getParent()).isDirectory();
    }

    @Test
    void prepareShouldMoveTheLegacyRootLogIntoTheMetadataDirectory() throws IOException {
        Files.writeString(backupRoot.resolve("warnings.log"), "alertas antigos");

        Path log = BackupMetadata.prepareWarningsLog(backupRoot);

        assertThat(log).hasContent("alertas antigos");
        assertThat(backupRoot.resolve("warnings.log")).doesNotExist();
    }

    @Test
    void prepareShouldNotTouchARootWarningsLogOnceTheMetadataDirectoryExists() throws IOException {
        BackupMetadata.prepareWarningsLog(backupRoot);
        // A partir daqui um warnings.log na raiz e copia de um arquivo do usuario.
        Files.writeString(backupRoot.resolve("warnings.log"), "do usuario");

        BackupMetadata.prepareWarningsLog(backupRoot);

        assertThat(backupRoot.resolve("warnings.log")).hasContent("do usuario");
    }

    @Test
    void locateShouldFallBackToTheRootLogOnlyForLegacyBackups() throws IOException {
        assertThat(BackupMetadata.locateWarningsLog(backupRoot)).isEqualTo(backupRoot.resolve("warnings.log"));

        BackupMetadata.prepareWarningsLog(backupRoot);

        assertThat(BackupMetadata.locateWarningsLog(backupRoot))
                .isEqualTo(backupRoot.resolve(".backup-manager").resolve("warnings.log"));
    }

    @Test
    void rootWarningsLogShouldBeMetadataOnlyInLegacyBackups() throws IOException {
        Path rootLog = backupRoot.resolve("warnings.log");
        assertThat(BackupMetadata.isMetadata(rootLog, backupRoot)).isTrue();
        assertThat(BackupMetadata.isMetadata(backupRoot.resolve("logs").resolve("warnings.log"), backupRoot)).isFalse();

        Files.createDirectories(backupRoot.resolve(".backup-manager"));

        assertThat(BackupMetadata.isMetadata(rootLog, backupRoot)).isFalse();
        assertThat(BackupMetadata.isMetadata(backupRoot.resolve(".backup-manager"), backupRoot)).isTrue();
        assertThat(BackupMetadata.isMetadata(BackupMetadata.warningsLog(backupRoot), backupRoot)).isTrue();
        assertThat(BackupMetadata.isMetadata(backupRoot.resolve("documento.txt"), backupRoot)).isFalse();
    }
}
