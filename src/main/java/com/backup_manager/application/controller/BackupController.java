package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.BackupConflictResponse;
import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.dto.BackupStartResponse;
import com.backup_manager.application.dto.BackupStatsResponse;
import com.backup_manager.application.dto.BackupTaskSummaryResponse;
import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;
    private final BackupRequestValidationService backupRequestValidationService;
    private final ProgressEmitter progressEmitter;
    private final BackupRepository backupRepository;
    private final BackupHistoryService historyService;

    public BackupController(BackupService backupService,
                            BackupRequestValidationService backupRequestValidationService,
                            ProgressEmitter progressEmitter,
                            BackupRepository backupRepository, BackupHistoryService historyService) {
        this.backupService = backupService;
        this.backupRequestValidationService = backupRequestValidationService;
        this.progressEmitter = progressEmitter;
        this.backupRepository = backupRepository;
        this.historyService = historyService;
    }

    @PostMapping("/start")
    public ResponseEntity<Object> startBackup(@Valid @RequestBody BackupRequest request) {
        try {
            List<String> sources = request.getSources();
            List<String> destinations = request.getDestination();

            backupRequestValidationService.validateExecutableRequest(sources, destinations);

            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                String destination = destinations.get(i);

                Optional<BackupTask> activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            BackupConflictResponse.of(
                                    "Ja existe um backup ativo para este par origem/destino",
                                    source,
                                    destination,
                                    activeTask.get().getId()
                            )
                    );
                }
            }

            List<Long> taskIds = new ArrayList<>();

            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                String destination = destinations.get(i);

                Long taskId = backupService.runBackup(source, destination);

                if (taskId != null) {
                    taskIds.add(taskId);
                }
            }

            return ResponseEntity.ok(
                    BackupStartResponse.of(
                            "Verificacoes concluidas. Backup(s) iniciado(s) com sucesso",
                            taskIds
                    )
            );

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca ao iniciar backup: {}", e.getMessage());
            return errorResponse(HttpStatus.FORBIDDEN, "Operacao de backup nao autorizada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Falha de validacao ao iniciar backup: {}", e.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, "Falha na validacao da solicitacao de backup.");
        } catch (Exception e) {
            logger.error("Erro inesperado ao iniciar backup", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao iniciar backup.");
        }
    }

    @GetMapping("/history")
    public ResponseEntity<Object> getBackupHistory() {
        try {
            List<BackupTask> tasks = backupService.getAllTasks();
            List<BackupResponse> responseList = new ArrayList<>();

            for (BackupTask task : tasks) {
                responseList.add(BackupResponse.fromTask(task));
            }

            return ResponseEntity.ok(responseList);

        } catch (Exception e) {
            logger.error("Erro ao listar historico de backups", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao listar historico.");
        }
    }

    @GetMapping("/history/search")
    public ResponseEntity<Object> searchHistory(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir
    ) {
        try {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Data inicial nao pode ser posterior a data final"));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Tamanho da pagina deve estar entre 1 e 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
            Page<BackupResponse> result = historyService.searchHistory(status, startDate, endDate, pageable);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Erro ao buscar historico", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar historico."));
        }
    }

    @GetMapping("/history/stats")
    public ResponseEntity<Object> getStatistics() {
        try {
            BackupStatsResponse stats = historyService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Erro ao calcular estatisticas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao calcular estatisticas."));
        }
    }

    @GetMapping("/history/recent")
    public ResponseEntity<Object> getRecentBackups(
            @RequestParam(defaultValue = "5") int limit
    ) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "Limite deve estar entre 1 e 100"));
            }

            List<BackupResponse> recent = historyService.getRecentBackups(limit);
            return ResponseEntity.ok(recent);

        } catch (Exception e) {
            logger.error("Erro ao buscar backups recentes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao buscar backups recentes."));
        }
    }

    @GetMapping("/progress")
    public SseEmitter streamProgress() {
        return progressEmitter.createEmitter();
    }

    @PostMapping("/{taskId}/pause")
    public ResponseEntity<OperationResponse> pauseBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.pauseBackup(taskId);
            if (success) {
                return successResponse("Backup pausado com sucesso", taskId);
            } else {
                return errorResponse(HttpStatus.NOT_FOUND, "Tarefa nao encontrada ou nao pode ser pausada", taskId);
            }
        } catch (Exception e) {
            logger.error("Erro ao pausar backup {}", taskId, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao pausar backup.", taskId);
        }
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<OperationResponse> resumeBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.resumeBackup(taskId);
            if (success) {
                return successResponse("Backup retomado com sucesso", taskId);
            } else {
                return errorResponse(HttpStatus.NOT_FOUND, "Tarefa nao encontrada ou nao pode ser retomada", taskId);
            }
        } catch (Exception e) {
            logger.error("Erro ao retomar backup {}", taskId, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao retomar backup.", taskId);
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<OperationResponse> cancelBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.cancelBackup(taskId);
            if (success) {
                return successResponse("Backup cancelado com sucesso", taskId);
            } else {
                return errorResponse(HttpStatus.NOT_FOUND, "Tarefa nao encontrada ou nao pode ser cancelada", taskId);
            }
        } catch (Exception e) {
            logger.error("Erro ao cancelar backup {}", taskId, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao cancelar backup.", taskId);
        }
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<Object> getTaskStatus(@PathVariable Long taskId) {
        Optional<BackupTask> task = backupRepository.findById(taskId);
        if (task.isPresent()) {
            return ResponseEntity.ok(BackupTaskSummaryResponse.fromTask(task.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(OperationResponse.error("Tarefa nao encontrada", taskId));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<BackupTaskSummaryResponse>> getActiveTasks() {
        // Busca apenas os status operacionais necessarios para evitar filtrar tudo em memoria.
        List<BackupTaskSummaryResponse> activeTasks = backupRepository.findByStatusIn(List.of(Status.EM_ANDAMENTO, Status.PAUSADO))
                .stream()
                .map(BackupTaskSummaryResponse::fromTask)
                .toList();

        return ResponseEntity.ok(activeTasks);
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message));
    }

    private ResponseEntity<OperationResponse> errorResponse(HttpStatus status, String message, Long taskId) {
        return ResponseEntity.status(status).body(OperationResponse.error(message, taskId));
    }

    private ResponseEntity<OperationResponse> successResponse(String message, Long taskId) {
        return ResponseEntity.ok(OperationResponse.success(message, taskId));
    }
}
