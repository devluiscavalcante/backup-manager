package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.LogContentResponse;
import com.backup_manager.application.dto.LogStatusResponse;
import com.backup_manager.infrastructure.logging.LogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ResponseEntity<LogStatusResponse> getLogStatus() {
        try {
            logService.resolveLatestWarningsLog();
        } catch (IOException e) {
            return ResponseEntity.ok(LogStatusResponse.unavailable("No backups executed yet in this session."));
        }

        return ResponseEntity.ok(LogStatusResponse.available("/warnings"));
    }

    @GetMapping("/warnings")
    public ResponseEntity<Object> getWarningsLog() {
        try {
            String content = logService.readLog(logService.resolveLatestWarningsLog());
            if (content.isBlank()) {
                return ResponseEntity.ok(LogContentResponse.message("warnings", "Nenhum alerta encontrado."));
            }

            return ResponseEntity.ok(LogContentResponse.content("warnings", content));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Nenhum warnings.log disponivel para consulta."));
        }
    }
}
