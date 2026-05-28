package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.FileTreeDTO;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.dto.PageResponse;
import com.backup_manager.application.dto.RestoreTaskResponse;
import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.application.service.RestoreService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.exception.BackupResourceNotFoundException;
import com.backup_manager.domain.exception.BackupStorageNotFoundException;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class RestoreController {

    private static final Logger logger = LoggerFactory.getLogger(RestoreController.class);

    private final RestoreService restoreService;
    private final RestoreRepository restoreRepository;
    private final SecurityAuditService securityAuditService;

    public RestoreController(RestoreService restoreService,
                             RestoreRepository restoreRepository,
                             SecurityAuditService securityAuditService) {
        this.restoreService = restoreService;
        this.restoreRepository = restoreRepository;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping("/backup/{id}/restore/preview")
    public ResponseEntity<Object> previewBackupFiles(@PathVariable Long id) {
        try {
            logger.info("Preview solicitado para backup ID={}", id);
            FileTreeDTO tree = restoreService.previewFiles(id);
            return ResponseEntity.ok(tree);

        } catch (BackupResourceNotFoundException | BackupStorageNotFoundException
                 | IllegalArgumentException | IllegalStateException | SecurityException e) {
            logger.warn("Falha ao gerar preview do backup {}: {}", id, e.getMessage());
            throw e;

        } catch (Exception e) {
            logger.error("Erro ao gerar preview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao gerar preview."));
        }
    }

    @PostMapping("/backup/{id}/restore")
    public ResponseEntity<Object> startFullRestore(@PathVariable Long id,
                                                   @Valid @RequestBody RestoreRequest request) {
        try {
            logger.info("Restauracao completa solicitada: backupId={}, target={}",
                    id, request.getTargetPath());

            Long taskId = restoreService.startFullRestore(id, request);
            securityAuditService.recordSuccess(
                    "restore.start_full",
                    "backup_restore_request",
                    Map.of("backupId", id, "taskId", taskId)
            );

            return ResponseEntity.ok(OperationResponse.restoreStarted(taskId, "Restauracao iniciada com sucesso"));

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca na restauracao: {}", e.getMessage());
            securityAuditService.recordFailure("restore.start_full", "backup_restore_request", "security_denied",
                    Map.of("backupId", id));
            throw e;

        } catch (BackupResourceNotFoundException | BackupStorageNotFoundException
                 | IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validacao na restauracao: {}", e.getMessage());
            securityAuditService.recordFailure("restore.start_full", "backup_restore_request", "validation_failed",
                    Map.of("backupId", id));
            throw e;

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauracao", e);
            securityAuditService.recordFailure("restore.start_full", "backup_restore_request", "internal_error",
                    Map.of("backupId", id));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao iniciar restauracao."));
        }
    }

    @PostMapping("/backup/{id}/restore/selective")
    public ResponseEntity<Object> startSelectiveRestore(@PathVariable Long id,
                                                        @Valid @RequestBody SelectiveRestoreRequest request) {
        int selectedFilesCount = request.getSelectedFiles() != null ? request.getSelectedFiles().size() : 0;
        try {
            logger.info("Restauracao seletiva solicitada: backupId={}, target={}, files={}",
                    id, request.getTargetPath(), selectedFilesCount);

            Long taskId = restoreService.startSelectiveRestore(id, request);
            securityAuditService.recordSuccess(
                    "restore.start_selective",
                    "backup_restore_request",
                    Map.of("backupId", id, "taskId", taskId, "selectedFilesCount", selectedFilesCount)
            );

            return ResponseEntity.ok(
                    OperationResponse.selectiveRestoreStarted(
                            taskId,
                            selectedFilesCount,
                            "Restauracao seletiva iniciada com sucesso"
                    )
            );

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca na restauracao seletiva: {}", e.getMessage());
            securityAuditService.recordFailure("restore.start_selective", "backup_restore_request", "security_denied",
                    Map.of("backupId", id, "selectedFilesCount", selectedFilesCount));
            throw e;

        } catch (BackupResourceNotFoundException | BackupStorageNotFoundException
                 | IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validacao na restauracao seletiva: {}", e.getMessage());
            securityAuditService.recordFailure("restore.start_selective", "backup_restore_request", "validation_failed",
                    Map.of("backupId", id, "selectedFilesCount", selectedFilesCount));
            throw e;

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauracao seletiva", e);
            securityAuditService.recordFailure("restore.start_selective", "backup_restore_request", "internal_error",
                    Map.of("backupId", id, "selectedFilesCount", selectedFilesCount));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao iniciar restauracao seletiva."));
        }
    }

    @PostMapping("/restore/{taskId}/cancel")
    public ResponseEntity<Object> cancelRestore(@PathVariable Long taskId) {
        try {
            logger.info("Cancelamento de restauracao solicitado: taskId={}", taskId);

            boolean success = restoreService.cancelRestore(taskId);

            if (success) {
                securityAuditService.recordSuccess("restore.cancel", "restore_task", Map.of("taskId", taskId));
                return ResponseEntity.ok(
                        OperationResponse.restoreCompleted(taskId, "CANCELADO", "Restauracao cancelada com sucesso")
                );
            } else {
                securityAuditService.recordFailure("restore.cancel", "restore_task", "task_not_found_or_invalid",
                        Map.of("taskId", taskId));
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Tarefa nao encontrada ou nao pode ser cancelada", taskId));
            }

        } catch (Exception e) {
            logger.error("Erro ao cancelar restauracao", e);
            securityAuditService.recordFailure("restore.cancel", "restore_task", "internal_error", Map.of("taskId", taskId));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao cancelar restauracao.", taskId));
        }
    }

    @GetMapping("/restore/{taskId}/status")
    public ResponseEntity<Object> getRestoreStatus(@PathVariable Long taskId) {
        try {
            Optional<RestoreTask> task = restoreRepository.findById(taskId);

            if (task.isPresent()) {
                return ResponseEntity.ok(RestoreTaskResponse.fromTask(task.get()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Tarefa de restauracao nao encontrada", taskId));
            }

        } catch (Exception e) {
            logger.error("Erro ao buscar status da restauracao", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar status da restauracao.", taskId));
        }
    }

    @GetMapping("/restore/history")
    public ResponseEntity<Object> getRestoreHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {
        try {
            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Tamanho da pagina deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
            Page<RestoreTaskResponse> history = restoreRepository.findAllOrderByStartedAtDesc(pageable)
                    .map(RestoreTaskResponse::fromTask);

            return ResponseEntity.ok(PageResponse.from(history));

        } catch (Exception e) {
            logger.error("Erro ao buscar historico de restauracoes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar historico de restauracoes."));
        }
    }

    @GetMapping("/restore/recent")
    public ResponseEntity<Object> getRecentRestores(
            @RequestParam(defaultValue = "5") int limit) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Limite deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(0, limit);
            List<RestoreTaskResponse> recent = restoreRepository.findTopNByOrderByStartedAtDesc(pageable)
                    .stream()
                    .map(RestoreTaskResponse::fromTask)
                    .toList();

            return ResponseEntity.ok(CollectionResponse.of(recent));

        } catch (Exception e) {
            logger.error("Erro ao buscar restauracoes recentes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar restauracoes recentes."));
        }
    }

    @GetMapping("/backup/{id}/restore/history")
    public ResponseEntity<Object> getBackupRestoreHistory(@PathVariable Long id) {
        try {
            List<RestoreTaskResponse> history = restoreRepository.findByBackupId(id)
                    .stream()
                    .map(RestoreTaskResponse::fromTask)
                    .toList();
            return ResponseEntity.ok(CollectionResponse.of(history));

        } catch (Exception e) {
            logger.error("Erro ao buscar historico de restauracoes do backup {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar historico de restauracoes do backup."));
        }
    }
}
