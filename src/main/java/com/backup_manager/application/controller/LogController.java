package com.backup_manager.application.controller;

import com.backup_manager.infrastructure.logging.LogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ResponseEntity<?> getLogStatus() {
        try {
            logService.resolveLatestWarningsLog();
        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                    "logsAvailable", false,
                    "availableLogs", new String[0],
                    "message", "No backups executed yet in this session."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "logsAvailable", true,
                "availableLogs", new String[]{"/warnings"}
        ));
    }

    @GetMapping("/warnings")
    public ResponseEntity<?> getWarningsLog() {
        try {
            String content = logService.redLog(logService.resolveLatestWarningsLog());
            if (content.isBlank()) {
                return ResponseEntity.ok(Map.of("message", "Nenhum alerta encontrado."));
            }

            return ResponseEntity.ok(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Nenhum warnings.log disponivel para consulta."));
        }
    }
}
