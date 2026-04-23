package com.backup_manager.application.controller;

import com.backup_manager.application.dto.FileTreeDTO;
import com.backup_manager.application.dto.RestoreRequest;
import com.backup_manager.application.dto.SelectiveRestoreRequest;
import com.backup_manager.application.service.RestoreService;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public ResponseEntity<?> previewBackupFiles(@PathVariable Long id) {
        try {
            logger.info("Preview solicitado para backup ID={}", id);
            FileTreeDTO tree = restoreService.previewFiles(id);
            return ResponseEntity.ok(tree);

        } catch (IllegalArgumentException e) {
            logger.warn("Backup não encontrado: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Backup inválido: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erro ao gerar preview: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao gerar preview: " + e.getMessage()));
        }
    }

    @PostMapping("/backup/{id}/restore")
    public ResponseEntity<?> startFullRestore(@PathVariable Long id,
                                              @RequestBody RestoreRequest request) {
        try {
            logger.info("Restauração completa solicitada: backupId={}, target={}",
                    id, request.getTargetPath());

            Long taskId = restoreService.startFullRestore(id, request);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "EM_ANDAMENTO");
            response.put("message", "Restauração iniciada com sucesso");

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            logger.warn("Bloqueio de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validação: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauração: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao iniciar restauração: " + e.getMessage()));
        }
    }

    @PostMapping("/backup/{id}/restore/selective")
    public ResponseEntity<?> startSelectiveRestore(@PathVariable Long id,
                                                   @RequestBody SelectiveRestoreRequest request) {
        try {
            int selectedFilesCount = request.getSelectedFiles() != null ? request.getSelectedFiles().size() : 0;
            logger.info("Restauração seletiva solicitada: backupId={}, target={}, files={}",
                    id, request.getTargetPath(), selectedFilesCount);

            Long taskId = restoreService.startSelectiveRestore(id, request);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "EM_ANDAMENTO");
            response.put("filesCount", selectedFilesCount);
            response.put("message", "Restauração seletiva iniciada com sucesso");

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            logger.warn("Bloqueio de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Erro de validação: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            logger.error("Erro ao iniciar restauração seletiva: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao iniciar restauração: " + e.getMessage()));
        }
    }

    @PostMapping("/restore/{taskId}/cancel")
    public ResponseEntity<?> cancelRestore(@PathVariable Long taskId) {
        try {
            logger.info("Cancelamento de restauração solicitado: taskId={}", taskId);

            boolean success = restoreService.cancelRestore(taskId);

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Restauração cancelada com sucesso"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Tarefa não encontrada ou não pode ser cancelada"));
            }

        } catch (Exception e) {
            logger.error("Erro ao cancelar restauração: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao cancelar restauração: " + e.getMessage()));
        }
    }

    @GetMapping("/restore/{taskId}/status")
    public ResponseEntity<?> getRestoreStatus(@PathVariable Long taskId) {
        try {
            Optional<RestoreTask> task = restoreRepository.findById(taskId);

            if (task.isPresent()) {
                return ResponseEntity.ok(task.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Tarefa de restauração não encontrada"));
            }

        } catch (Exception e) {
            logger.error("Erro ao buscar status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao buscar status: " + e.getMessage()));
        }
    }

    @GetMapping("/restore/history")
    public ResponseEntity<?> getRestoreHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {
        try {
            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tamanho da página deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
            Page<RestoreTask> history = restoreRepository.findAllOrderByStartedAtDesc(pageable);

            return ResponseEntity.ok(history);

        } catch (Exception e) {
            logger.error("Erro ao buscar histórico: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao buscar histórico: " + e.getMessage()));
        }
    }

    @GetMapping("/restore/recent")
    public ResponseEntity<?> getRecentRestores(
            @RequestParam(defaultValue = "5") int limit) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Limite deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(0, limit);
            List<RestoreTask> recent = restoreRepository.findTopNByOrderByStartedAtDesc(pageable);

            return ResponseEntity.ok(recent);

        } catch (Exception e) {
            logger.error("Erro ao buscar restaurações recentes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao buscar restaurações: " + e.getMessage()));
        }
    }

    @GetMapping("/backup/{id}/restore/history")
    public ResponseEntity<?> getBackupRestoreHistory(@PathVariable Long id) {
        try {
            List<RestoreTask> history = restoreRepository.findByBackupId(id);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            logger.error("Erro ao buscar histórico do backup: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao buscar histórico: " + e.getMessage()));
        }
    }
}
