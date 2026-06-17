package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.service.SchemaDiagnosticsService;
import com.backup_manager.application.service.SystemStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemControllerTests {

    @Test
    void getStorageStatsShouldReturnStructuredInternalServerErrorWhenStorageServiceFails() {
        SystemStorageService storageService = mock(SystemStorageService.class);
        SystemController controller = new SystemController(
                storageService,
                mock(SchemaDiagnosticsService.class)
        );

        when(storageService.getStorageInfo()).thenThrow(new RuntimeException("storage_unavailable"));

        ResponseEntity<Object> response = controller.getStorageStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel carregar as informacoes de armazenamento.");
        assertThat(body.getCode()).isEqualTo("system_storage_failed");
        assertThat(body.getPath()).isEqualTo("/api/system/storage");
        assertThat(body.getDetails()).isNull();
    }

    @Test
    void getSchemaStatusShouldReturnStructuredInternalServerErrorWhenSchemaDiagnosticsFails() {
        SchemaDiagnosticsService schemaDiagnosticsService = mock(SchemaDiagnosticsService.class);
        SystemController controller = new SystemController(
                mock(SystemStorageService.class),
                schemaDiagnosticsService
        );

        when(schemaDiagnosticsService.inspect()).thenThrow(new RuntimeException("schema_unavailable"));

        ResponseEntity<Object> response = controller.getSchemaStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel inspecionar o schema do banco de dados.");
        assertThat(body.getCode()).isEqualTo("system_schema_failed");
        assertThat(body.getPath()).isEqualTo("/api/system/schema");
        assertThat(body.getDetails()).isNull();
    }
}
