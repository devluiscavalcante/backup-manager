package com.backup_manager.application.service;

import com.backup_manager.application.dto.FileNodeDTO;
import com.backup_manager.application.dto.FileTreeDTO;
import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.domain.exception.BackupStorageNotFoundException;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.domain.service.RestoreTaskManager;
import com.backup_manager.infrastructure.config.AppSecurityProperties;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import com.backup_manager.infrastructure.storage.FileRestoreOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestoreServiceTests {

    private static final Long BACKUP_ID = 3L;

    @TempDir
    Path tempDir;

    private final BackupRepository backupRepository = mock(BackupRepository.class);
    private final RestoreRepository restoreRepository = mock(RestoreRepository.class);
    private final RestoreService asyncProxy = mock(RestoreService.class);
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
                asyncProxy
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

    @Test
    void shouldFailTheSavedRestoreWhenTheQueueIsFull() {
        RestoreRequest request = new RestoreRequest();
        request.setTargetPath(tempDir.resolve("restored").toString());
        when(restoreRepository.save(any(RestoreTask.class))).thenAnswer(invocation -> {
            RestoreTask saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        doThrow(new TaskRejectedException("fila cheia"))
                .when(asyncProxy).processRestoreAsync(any(), anyBoolean(), any());

        assertThatThrownBy(() -> service.startFullRestore(BACKUP_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fila de restauracoes cheia");

        // A tarefa nao pode ficar EM_ANDAMENTO sem worker.
        ArgumentCaptor<RestoreTask> saved = ArgumentCaptor.forClass(RestoreTask.class);
        verify(restoreRepository, times(2)).save(saved.capture());
        RestoreTask last = saved.getValue();
        assertThat(last.getStatus()).isEqualTo(RestoreStatus.FALHA);
        assertThat(last.getErrorMessage()).isEqualTo("Fila de restauracoes cheia: tarefa nao iniciada");
        assertThat(last.getFinishedAt()).isNotNull();
    }

    @Test
    void shouldFailTheSavedSelectiveRestoreWhenTheQueueIsFull() {
        SelectiveRestoreRequest request = new SelectiveRestoreRequest();
        request.setTargetPath(tempDir.resolve("restored").toString());
        request.setSelectedFiles(List.of("file.txt"));
        when(restoreRepository.save(any(RestoreTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new TaskRejectedException("fila cheia"))
                .when(asyncProxy).processRestoreAsync(any(), anyBoolean(), any());

        assertThatThrownBy(() -> service.startSelectiveRestore(BACKUP_ID, request))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<RestoreTask> saved = ArgumentCaptor.forClass(RestoreTask.class);
        verify(restoreRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RestoreStatus.FALHA);
    }

    @Test
    void previewShouldShowTheSnapshotOfTheHistoryEntryInsteadOfTheCurrentDestination() throws IOException {
        // Raiz com o estado atual; o snapshot da entrada guarda a versao daquela execucao.
        Path snapshot = Files.createDirectories(backupRoot.resolve(".backup-manager/snapshots/20261028-100000-3"));
        Files.writeString(snapshot.resolve("antigo.txt"), "versao da execucao 3");
        backup().setSnapshotPath(snapshot.toString());

        FileTreeDTO tree = service.previewFiles(BACKUP_ID);

        assertThat(tree.getFiles()).extracting(FileNodeDTO::getName).containsExactly("antigo.txt");
        assertThat(tree.getBackupPath()).isEqualTo(snapshot.toString());
        assertThat(tree.isLegacyMirror()).isFalse();
        assertThat(tree.getWarning()).isNull();
    }

    @Test
    void previewOfALegacyBackupShouldWarnThatItIsTheCurrentDestinationState() throws IOException {
        FileTreeDTO tree = service.previewFiles(BACKUP_ID);

        assertThat(tree.isLegacyMirror()).isTrue();
        assertThat(tree.getWarning()).isEqualTo(RestoreService.LEGACY_MIRROR_WARNING);
    }

    @Test
    void restoreOfASnapshotRemovedByRetentionShouldExplainWhy() {
        backup().setSnapshotPath(backupRoot.resolve(".backup-manager/snapshots/20260101-100000-3").toString());
        backup().setSnapshotRemovedAt(LocalDateTime.now());
        RestoreRequest request = new RestoreRequest();
        request.setTargetPath(tempDir.resolve("restored").toString());

        assertThatThrownBy(() -> service.startFullRestore(BACKUP_ID, request))
                .isInstanceOf(BackupStorageNotFoundException.class)
                .hasMessage("O snapshot deste backup foi removido pela politica de retencao.");
        assertThatThrownBy(() -> service.previewFiles(BACKUP_ID))
                .isInstanceOf(BackupStorageNotFoundException.class);
        verify(restoreRepository, never()).save(any());
    }

    @Test
    void previewShouldListOnlyDirectChildrenWithoutWalkingTheSubtree() throws IOException {
        Path deep = Files.createDirectories(backupRoot.resolve("docs").resolve("2026").resolve("10"));
        Files.writeString(deep.resolve("relatorio.txt"), "conteudo");
        backup().setFileCount(2L);
        backup().setTotalSizeMB(new BigDecimal("1.50"));

        FileTreeDTO root = service.previewFiles(BACKUP_ID);

        assertThat(root.getFiles()).extracting(FileNodeDTO::getName).containsExactly("docs", "file.txt");
        FileNodeDTO docs = root.getFiles().get(0);
        // Pasta sem totais recursivos: o conteudo vem ao abri-la.
        assertThat(docs.getType()).isEqualTo("directory");
        assertThat(docs.getFileCount()).isNull();
        assertThat(docs.getSizeMB()).isNull();
        // Os totais sao os registrados pela execucao, nao uma varredura a cada GET.
        assertThat(root.getTotalFiles()).isEqualTo(2L);
        assertThat(root.getTotalSizeMB()).isEqualByComparingTo("1.50");

        FileTreeDTO opened = service.previewFiles(BACKUP_ID, "docs/2026/10");
        assertThat(opened.getFiles()).extracting(FileNodeDTO::getRelativePath)
                .containsExactly(Path.of("docs", "2026", "10", "relatorio.txt").toString());
        assertThat(opened.getPath()).isEqualTo(Path.of("docs", "2026", "10").toString());
    }

    @Test
    void previewShouldRejectPathsOutsideTheBackupOrIntoItsMetadata() throws IOException {
        Files.createDirectories(backupRoot.resolve(".backup-manager").resolve("logs"));

        assertThatThrownBy(() -> service.previewFiles(BACKUP_ID, "../"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.previewFiles(BACKUP_ID, tempDir.toString()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.previewFiles(BACKUP_ID, ".backup-manager/logs"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.previewFiles(BACKUP_ID, "nao-existe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void previewOfAHugeDirectoryShouldBeTruncated() throws IOException {
        Path big = Files.createDirectories(backupRoot.resolve("grande"));
        for (int i = 0; i <= RestoreService.MAX_PREVIEW_ENTRIES; i++) {
            Files.createFile(big.resolve("f" + i));
        }

        FileTreeDTO tree = service.previewFiles(BACKUP_ID, "grande");

        assertThat(tree.getFiles()).hasSize(RestoreService.MAX_PREVIEW_ENTRIES);
        assertThat(tree.isTruncated()).isTrue();
    }

    private BackupTask backup() {
        return backupRepository.findById(BACKUP_ID).orElseThrow();
    }
}
