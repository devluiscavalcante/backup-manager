package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.exception.BackupInitializationException;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupControllerTests {

    @Test
    void startBackupShouldReturnFailureWhenTaskInitializationFails() {
        BackupService backupService = mock(BackupService.class);
        BackupRequestValidationService validationService = mock(BackupRequestValidationService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        BackupController controller = new BackupController(
                backupService,
                validationService,
                mock(ProgressEmitter.class),
                mock(BackupRepository.class),
                mock(BackupHistoryService.class),
                securityAuditService
        );
        BackupRequest request = new BackupRequest();
        request.setSources(List.of("source"));
        request.setDestination(List.of("destination"));

        when(backupService.getActiveTask("source", "destination")).thenReturn(Optional.empty());
        when(backupService.runBackup("source", "destination"))
                .thenThrow(new BackupInitializationException(
                        "Falha ao preparar o backup.",
                        new RuntimeException("disk_unavailable")
                ));

        ResponseEntity<Object> response = controller.startBackup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Erro interno ao preparar backup.");
        assertThat(body.getCode()).isEqualTo("backup_start_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/start");
        assertThat(body.getDetails()).isEqualTo(Map.of("sourceCount", 1, "destinationCount", 1));

        verify(securityAuditService).recordFailure(
                "backup.start",
                "backup_request",
                "initialization_failed",
                Map.of("sourceCount", 1, "destinationCount", 1)
        );
        verify(securityAuditService, never()).recordSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                anyMap()
        );
    }

    @Test
    void getTaskStatusShouldReturnStructuredInternalServerErrorWhenRepositoryFails() {
        BackupRepository backupRepository = mock(BackupRepository.class);
        BackupController controller = new BackupController(
                mock(BackupService.class),
                mock(BackupRequestValidationService.class),
                mock(ProgressEmitter.class),
                backupRepository,
                mock(BackupHistoryService.class),
                mock(SecurityAuditService.class)
        );

        when(backupRepository.findById(99L)).thenThrow(new RuntimeException("database_unavailable"));

        ResponseEntity<Object> response = controller.getTaskStatus(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Erro interno ao buscar status do backup.");
        assertThat(body.getCode()).isEqualTo("backup_task_operation_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/99/status");
        assertThat(body.getTaskId()).isEqualTo(99L);
        assertThat(body.getDetails()).isEqualTo(Map.of("taskId", 99L));
    }
}
