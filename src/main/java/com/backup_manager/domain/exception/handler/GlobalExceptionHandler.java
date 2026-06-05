package com.backup_manager.domain.exception.handler;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.domain.exception.BackupResourceNotFoundException;
import com.backup_manager.domain.exception.BackupStorageNotFoundException;
import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFolderNotFoundException(FolderNotFoundException ex,
                                                                          WebRequest request) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        "source_folder_not_found",
                        Map.of("source", ex.getPath()),
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(FolderEmptyException.class)
    public ResponseEntity<ApiErrorResponse> handleFolderEmptyException(FolderEmptyException ex,
                                                                       WebRequest request) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        "source_folder_empty",
                        Map.of("source", ex.getPath()),
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(DestinationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDestinationNotFoundException(DestinationNotFoundException ex,
                                                                               WebRequest request) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        "destination_folder_not_found",
                        destinationDetails(ex),
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(BackupResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBackupResourceNotFoundException(BackupResourceNotFoundException ex,
                                                                                  WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage(),
                        "backup_not_found",
                        Map.of("backupId", ex.getBackupId()),
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(BackupStorageNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBackupStorageNotFoundException(BackupStorageNotFoundException ex,
                                                                                 WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiErrorResponse.of(
                        HttpStatus.CONFLICT,
                        ex.getMessage(),
                        "backup_storage_not_found",
                        Map.of("backupId", ex.getBackupId(), "backupPath", ex.getBackupPath()),
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                           WebRequest request) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        "operation_validation_failed",
                        null,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(IllegalStateException ex,
                                                                        WebRequest request) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        "operation_precondition_failed",
                        null,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException ex,
                                                                    WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN,
                        ex.getMessage(),
                        "operation_not_allowed",
                        null,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                       WebRequest request) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        "Falha na validacao dos dados enviados.",
                        "request_validation_failed",
                        validationErrors,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                             WebRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("parameter", ex.getName());
        details.put("value", ex.getValue());

        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null) {
            details.put("expectedType", requiredType.getSimpleName());
            if (requiredType.isEnum()) {
                details.put("allowedValues", Arrays.stream(requiredType.getEnumConstants())
                        .map(Object::toString)
                        .toList());
            }
        }

        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        "Parametro de requisicao invalido.",
                        "request_parameter_type_mismatch",
                        details,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                         WebRequest request) {
        logger.warn("Corpo da requisicao invalido: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        "Corpo da requisicao invalido ou malformado.",
                        "invalid_request_body",
                        null,
                        extractPath(request)
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllExceptions(Exception ex, WebRequest request) {
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

        logger.error("Erro nao tratado: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno inesperado.",
                        "unexpected_error",
                        null,
                        extractPath(request)
                )
        );
    }

    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description == null || !description.startsWith("uri=")) {
            return null;
        }

        return description.substring(4);
    }

    private Map<String, Object> destinationDetails(DestinationNotFoundException ex) {
        if (ex.getPath() == null) {
            return null;
        }

        return Map.of("destination", ex.getPath());
    }
}
