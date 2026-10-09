package com.backup_manager.application.service;

import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.service.RestoreTaskManager;
import com.backup_manager.infrastructure.config.AppSecurityProperties;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import com.backup_manager.infrastructure.storage.FileRestoreOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestoreServiceTests {

    private static final Long BACKUP_ID = 3L;

    @TempDir
    Path tempDir;

    private final BackupRepository backupRepository = mock(BackupRepository.class);
    private final RestoreRepository restoreRepository = mock(RestoreRepository.class);
    private Path backupRoot;
    private RestoreService service;

    @BeforeEach
    void setUp() throws IOException {
        backupRoot = Files.createDirectories(tempDir.resolve("backup"));
        Files.writeString(backupRoot.resolve("file.txt"), "content");

        BackupTask backup = new BackupTask();
        backup.setId(BACKUP_ID);
        backup.setSourcePath(tempDir.resolve("source").toString());
        backup.setDestinationPath(backupRoot.toString());
        when(backupRepository.findById(BACKUP_ID)).thenReturn(Optional.of(backup));

        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setAllowedPathRoots(List.of(tempDir.toString()));

        service = new RestoreService(
                backupRepository,
                restoreRepository,
                mock(RestoreTaskManager.class),
                mock(FileRestoreOperations.class),
                mock(ApplicationEventPublisher.class),
                new ObjectMapper(),
                new PathSecurityService(properties),
                mock(RestoreService.class)
        );
    }

    @Test
    void shouldRejectFullRestoreIntoTheBackupItself() {
        RestoreRequest request = new RestoreRequest();
        request.setTargetPath(backupRoot.resolve("restored").toString());

        assertThatThrownBy(() -> service.startFullRestore(BACKUP_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Origem e destino nao podem estar um dentro do outro na operacao de restauracao.");

        verify(restoreRepository, never()).save(any());
    }

    @Test
    void shouldRejectSelectiveRestoreIntoAnAncestorOfTheBackup() {
        SelectiveRestoreRequest request = new SelectiveRestoreRequest();
        request.setTargetPath(tempDir.toString());
        request.setSelectedFiles(List.of("file.txt"));

        assertThatThrownBy(() -> service.startSelectiveRestore(BACKUP_ID, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(restoreRepository, never()).save(any());
    }
}
