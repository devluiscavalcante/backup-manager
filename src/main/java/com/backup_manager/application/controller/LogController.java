package com.backup_manager.application.controller;

import com.backup_manager.infrastructure.logging.BackupContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final BackupContext backupContext;

    public LogController(BackupContext backupContext) {
        this.backupContext = backupContext;
    }

    @GetMapping("/warnings")

    public ResponseEntity<String> getWarningsLog() {
        try {
            String lastDest = backupContext.getLastDestination();

            if (lastDest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Nenhum backup foi executado nesta sessão ainda.");
            }

            Path logPath = Path.of(lastDest, "warnings.log");
            if (!Files.exists(logPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("O arquivo warnings.log não foi encontrado em: " + logPath);
            }

            String content = Files.readString(logPath);
            if (content.isBlank()) {
                return ResponseEntity.ok("Nenhum alerta encontrado — o backup foi concluído sem warnings.");
            }

            return ResponseEntity.ok(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao ler warnings.log: " + e.getMessage());
        }
    }
}