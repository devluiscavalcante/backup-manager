package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import com.backup_manager.application.dto.ScheduledBackupRequest;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.CronValidationService;
import com.backup_manager.application.service.DynamicSchedulerService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupConfigControllerTests {

    @Test
    void createOrUpdateShouldReturnInternalServerErrorWhenSaveFailsUnexpectedly() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        CronValidationService cronValidationService = mock(CronValidationService.class);
        BackupConfigController controller = new BackupConfigController(
                mock(ApplicationEventPublisher.class),
                repository,
                mock(DynamicSchedulerService.class),
                mock(BackupRequestValidationService.class),
                cronValidationService,
                mock(SecurityAuditService.class)
        );

        when(cronValidationService.validateCronExpression("0 0 2 * * *"))
                .thenReturn(CronValidationResponse.of(true, "Todos os dias as 2:00 AM", null, List.of()));
        when(repository.save(any(ScheduledBackupEntity.class)))
                .thenThrow(new RuntimeException("database_unavailable"));

        ResponseEntity<Object> response = controller.createOrUpdate(validRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel salvar a configuracao de backup.");
        assertThat(body.getCode()).isEqualTo("scheduler_config_save_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/config");
        assertThat(body.getDetails()).isNull();
    }

    @Test
    void createOrUpdateShouldReturnStructuredBadRequestWhenValidationFails() {
        BackupRequestValidationService validationService = mock(BackupRequestValidationService.class);
        BackupConfigController controller = new BackupConfigController(
                mock(ApplicationEventPublisher.class),
                mock(ScheduledBackupRepository.class),
                mock(DynamicSchedulerService.class),
                validationService,
                mock(CronValidationService.class),
                mock(SecurityAuditService.class)
        );

        doThrow(new IllegalArgumentException("A pasta de origem esta vazia"))
                .when(validationService)
                .validateSchedulableRequest(List.of("C:/temp/source"), List.of("C:/temp/destination"));

        ResponseEntity<Object> response = controller.createOrUpdate(validRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("A pasta de origem esta vazia");
        assertThat(body.getCode()).isEqualTo("scheduler_config_validation_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/config");
        assertThat(body.getDetails()).isNull();
    }

    @Test
    void listAllShouldReturnStructuredInternalServerErrorWhenRepositoryFails() {
        ScheduledBackupRepository repository = mock(ScheduledBackupRepository.class);
        BackupConfigController controller = new BackupConfigController(
                mock(ApplicationEventPublisher.class),
                repository,
                mock(DynamicSchedulerService.class),
                mock(BackupRequestValidationService.class),
                mock(CronValidationService.class),
                mock(SecurityAuditService.class)
        );

        when(repository.findAll()).thenThrow(new RuntimeException("database_unavailable"));

        ResponseEntity<Object> response = controller.listAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel listar as configuracoes de backup.");
        assertThat(body.getCode()).isEqualTo("scheduler_config_list_failed");
        assertThat(body.getPath()).isEqualTo("/api/backup/config");
        assertThat(body.getDetails()).isNull();
    }

    private ScheduledBackupRequest validRequest() {
        ScheduledBackupRequest request = new ScheduledBackupRequest();
        request.setName("Backup diario");
        request.setSources(List.of("C:/temp/source"));
        request.setDestinations(List.of("C:/temp/destination"));
        request.setCronExpression("0 0 2 * * *");
        return request;
    }
}
