package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.FileTreeDTO;
import com.backup_manager.application.dto.RestoreOperationResponse;
import com.backup_manager.application.dto.RestoreTaskResponse;
import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.application.service.RestoreService;
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
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class RestoreController {

    private static final Logger logger = LoggerFactory.getLogger(RestoreController.class);

    private final RestoreService restoreService;
    private final RestoreRepository restoreRepository;

    public RestoreController(RestoreService restoreService, RestoreRepository restoreRepository) {
        this.restoreService = restoreService;
        this.restoreRepository = restoreRepository;
    }

    @GetMapping("/backup/{id}/restore/preview")
    public ResponseEntity<Object> previewBackupFiles(@PathVariable Long id) {
        try {
            logger.info("Preview solicitado para backup ID={}", id);
            FileTreeDTO tree = restoreService.previewFiles(id);
            return ResponseEntity.ok(tree);

        } catch (IllegalArgumentException e) {
            logger.warn("Backup nao encontrado: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Backup nao encontrado para preview."));

        } catch (IllegalStateException e) {
            logger.warn("Backup invalido para preview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Nao foi possivel gerar preview para o backup informado."));

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

            return ResponseEntity.ok(RestoreOperationResponse.started(taskId, "Restauracao iniciada com sucesso"));

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca na restauracao: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(HttpStatus.FORBIDDEN, "Operacao de restauracao nao autorizada."));

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validacao na restauracao: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Falha na validacao da solicitacao de restauracao."));

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauracao", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao iniciar restauracao."));
        }
    }

    @PostMapping("/backup/{id}/restore/selective")
    public ResponseEntity<Object> startSelectiveRestore(@PathVariable Long id,
                                                        @Valid @RequestBody SelectiveRestoreRequest request) {
        try {
            int selectedFilesCount = request.getSelectedFiles() != null ? request.getSelectedFiles().size() : 0;
            logger.info("Restauracao seletiva solicitada: backupId={}, target={}, files={}",
                    id, request.getTargetPath(), selectedFilesCount);

            Long taskId = restoreService.startSelectiveRestore(id, request);

            return ResponseEntity.ok(
                    RestoreOperationResponse.selectiveStarted(
                            taskId,
                            selectedFilesCount,
                            "Restauracao seletiva iniciada com sucesso"
                    )
            );

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca na restauracao seletiva: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(HttpStatus.FORBIDDEN, "Operacao de restauracao seletiva nao autorizada."));

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validacao na restauracao seletiva: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Falha na validacao da solicitacao de restauracao seletiva."));

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauracao seletiva", e);
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
                return ResponseEntity.ok(
                        RestoreOperationResponse.completed(taskId, "CANCELADO", "Restauracao cancelada com sucesso")
                );
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Tarefa nao encontrada ou nao pode ser cancelada", taskId));
            }

        } catch (Exception e) {
            logger.error("Erro ao cancelar restauracao", e);
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

            return ResponseEntity.ok(history);

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

            return ResponseEntity.ok(recent);

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
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            logger.error("Erro ao buscar historico de restauracoes do backup {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar historico de restauracoes do backup."));
        }
    }
}
