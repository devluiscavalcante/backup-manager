package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.LogContentResponse;
import com.backup_manager.application.dto.LogStatusResponse;
import com.backup_manager.application.dto.MutationResponse;
import com.backup_manager.infrastructure.logging.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger(LogController.class);
    private static final String LOGS_PATH = "/api/logs";
    private static final String WARNINGS_LOG_PATH = "/api/logs/warnings";

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ResponseEntity<Object> getLogStatus() {
        try {
            logService.resolveLatestWarningsLog();
        } catch (IOException e) {
            return ResponseEntity.ok(MutationResponse.success(
                    LogStatusResponse.unavailable("No backups executed yet in this session."),
                    "Status dos logs carregado com sucesso"
            ));
        } catch (Exception e) {
            logger.error("Erro ao carregar status dos logs", e);
            return internalError(
                    "Nao foi possivel carregar o status dos logs.",
                    "logs_status_failed",
                    LOGS_PATH
            );
        }

        return ResponseEntity.ok(MutationResponse.success(
                LogStatusResponse.available("/warnings"),
                "Status dos logs carregado com sucesso"
        ));
    }

    @GetMapping("/warnings")
    public ResponseEntity<Object> getWarningsLog() {
        try {
            String content = logService.readLog(logService.resolveLatestWarningsLog());
            if (content.isBlank()) {
                return ResponseEntity.ok(MutationResponse.success(
                        LogContentResponse.message("warnings", "Nenhum alerta encontrado."),
                        "Conteudo de log carregado com sucesso"
                ));
            }

            return ResponseEntity.ok(MutationResponse.success(
                    LogContentResponse.content("warnings", content),
                    "Conteudo de log carregado com sucesso"
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(
                            HttpStatus.NOT_FOUND,
                            "Nenhum warnings.log disponivel para consulta.",
                            "warnings_log_not_found",
                            null,
                            WARNINGS_LOG_PATH
                    ));
        } catch (Exception e) {
            logger.error("Erro ao carregar warnings.log", e);
            return internalError(
                    "Nao foi possivel carregar o warnings.log.",
                    "warnings_log_read_failed",
                    WARNINGS_LOG_PATH
            );
        }
    }

    private ResponseEntity<Object> internalError(String message, String code, String path) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, message, code, null, path));
    }
}
