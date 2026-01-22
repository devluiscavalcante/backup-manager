package com.backup_manager.domain.exception.handler;

import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({FolderNotFoundException.class, FolderEmptyException.class, DestinationNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleBackupExceptions(RuntimeException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, WebRequest request) {
        // Verifica se é uma requisição SSE
        String requestPath = request.getDescription(false);

        // Se for o endpoint SSE, não tratar a exceção (deixar propagar)
        if (requestPath != null && requestPath.contains("/api/backup/progress")) {
            logger.warn("Exceção SSE ignorada (não deve retornar ResponseEntity): {}", ex.getMessage());
            // Para SSE, não retornar ResponseEntity - deixa a exceção propagar
            // ou retorna null para não interferir
            // ou retorna null para não interferir
            return null;
        }

        // Também verifica se é um erro de conversão SSE
        if (ex instanceof org.springframework.http.converter.HttpMessageNotWritableException) {
            String message = ex.getMessage();
            if (message != null && message.contains("text/event-stream")) {
                logger.warn("Erro de conversão SSE ignorado: {}", ex.getMessage());
                return null;
            }
        }

        // Para todas as outras exceções, trata normalmente
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("error", "Erro inesperado: " + ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        logger.error("Erro não tratado: {}", ex.getMessage(), ex);

        return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}