package com.backup_manager.domain.exception.handler;

import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        errorBody.put("error", "Falha na validacao da operacao solicitada.");
        errorBody.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", "Falha na validacao dos dados enviados.");
        errorBody.put("details", validationErrors);
        errorBody.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, WebRequest request) {
        String requestPath = request.getDescription(false);

        if (requestPath != null && requestPath.contains("/api/backup/progress")) {
            logger.warn("Excecao SSE ignorada: {}", ex.getMessage());
            return null;
        }

        if (ex instanceof HttpMessageNotWritableException) {
            String message = ex.getMessage();
            if (message != null && message.contains("text/event-stream")) {
                logger.warn("Erro de conversao SSE ignorado: {}", ex.getMessage());
                return null;
            }
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("error", "Erro interno inesperado.");
        errorBody.put("timestamp", LocalDateTime.now());

        logger.error("Erro nao tratado: {}", ex.getMessage(), ex);

        return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
