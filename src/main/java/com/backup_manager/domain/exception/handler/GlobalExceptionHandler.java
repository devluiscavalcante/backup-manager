package com.backup_manager.domain.exception.handler;

import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({FolderNotFoundException.class, FolderEmptyException.class, DestinationNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleBackupExceptions(RuntimeException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("error", "Erro inesperado: " + ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
