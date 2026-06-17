package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.infrastructure.logging.LogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogControllerTests {

    @Test
    void getLogStatusShouldReturnStructuredInternalServerErrorWhenLogServiceFailsUnexpectedly() throws Exception {
        LogService logService = mock(LogService.class);
        LogController controller = new LogController(logService);

        when(logService.resolveLatestWarningsLog()).thenThrow(new RuntimeException("log_index_unavailable"));

        ResponseEntity<Object> response = controller.getLogStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel carregar o status dos logs.");
        assertThat(body.getCode()).isEqualTo("logs_status_failed");
        assertThat(body.getPath()).isEqualTo("/api/logs");
        assertThat(body.getDetails()).isNull();
    }

    @Test
    void getWarningsLogShouldReturnStructuredInternalServerErrorWhenLogReadFailsUnexpectedly() throws Exception {
        LogService logService = mock(LogService.class);
        LogController controller = new LogController(logService);
        Path logPath = Path.of("C:/backup/warnings.log");

        when(logService.resolveLatestWarningsLog()).thenReturn(logPath);
        when(logService.readLog(logPath)).thenThrow(new RuntimeException("log_read_unavailable"));

        ResponseEntity<Object> response = controller.getWarningsLog();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);

        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Nao foi possivel carregar o warnings.log.");
        assertThat(body.getCode()).isEqualTo("warnings_log_read_failed");
        assertThat(body.getPath()).isEqualTo("/api/logs/warnings");
        assertThat(body.getDetails()).isNull();
    }
}
