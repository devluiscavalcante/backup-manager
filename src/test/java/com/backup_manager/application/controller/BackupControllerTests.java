package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupControllerTests {

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
