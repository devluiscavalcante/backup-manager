package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.dto.BackupResponse;
import com.backup_manager.application.dto.BackupStatsResponse;
import com.backup_manager.application.dto.BackupTaskSummaryResponse;
import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.OperationResponse;
import com.backup_manager.application.dto.PageResponse;
import com.backup_manager.application.progress.ProgressEmitter;
import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
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
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);
    private static final String BACKUP_START_PATH = "/api/backup/start";
    private static final String BACKUP_HISTORY_PATH = "/api/backup/history";
    private static final String BACKUP_HISTORY_SEARCH_PATH = "/api/backup/history/search";
    private static final String BACKUP_HISTORY_STATS_PATH = "/api/backup/history/stats";
    private static final String BACKUP_HISTORY_RECENT_PATH = "/api/backup/history/recent";

    private final BackupService backupService;
    private final BackupRequestValidationService backupRequestValidationService;
    private final ProgressEmitter progressEmitter;
    private final BackupRepository backupRepository;
    private final BackupHistoryService historyService;
    private final SecurityAuditService securityAuditService;

    public BackupController(BackupService backupService,
                            BackupRequestValidationService backupRequestValidationService,
                            ProgressEmitter progressEmitter,
                            BackupRepository backupRepository,
                            BackupHistoryService historyService,
                            SecurityAuditService securityAuditService) {
        this.backupService = backupService;
        this.backupRequestValidationService = backupRequestValidationService;
        this.progressEmitter = progressEmitter;
        this.backupRepository = backupRepository;
        this.historyService = historyService;
        this.securityAuditService = securityAuditService;
    }

    @PostMapping("/start")
    public ResponseEntity<Object> startBackup(@Valid @RequestBody BackupRequest request) {
        List<String> sources = request.getSources() == null ? List.of() : request.getSources();
        List<String> destinations = request.getDestination() == null ? List.of() : request.getDestination();
        try {
            backupRequestValidationService.validateExecutableRequest(sources, destinations);

            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                String destination = destinations.get(i);

                Optional<BackupTask> activeTask = backupService.getActiveTask(source, destination);
                if (activeTask.isPresent()) {
                    securityAuditService.recordFailure(
                            "backup.start",
                            "backup_request",
                            "active_backup_conflict",
                            Map.of(
                                    "conflictingTaskId", activeTask.get().getId(),
                                    "sourceCount", sources.size(),
                                    "destinationCount", destinations.size()
                            )
                    );
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            ApiErrorResponse.of(
                                    HttpStatus.CONFLICT,
                                    "Ja existe um backup ativo para este par origem/destino",
                                    "active_backup_conflict",
                                    conflictDetails(source, destination, activeTask.get().getId()),
                                    "/api/backup/start",
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

            securityAuditService.recordSuccess(
                    "backup.start",
                    "backup_request",
                    Map.of(
                            "taskCount", taskIds.size(),
                            "taskIds", taskIds
                    )
            );
            return ResponseEntity.ok(
                    OperationResponse.backupStarted(
                            "Verificacoes concluidas. Backup(s) iniciado(s) com sucesso",
                            taskIds
                    )
            );

        } catch (SecurityException e) {
            logger.warn("Bloqueio de seguranca ao iniciar backup: {}", e.getMessage());
            securityAuditService.recordFailure("backup.start", "backup_request", "security_denied",
                    Map.of("sourceCount", sources.size(), "destinationCount", destinations.size()));
            throw e;
        } catch (FolderNotFoundException | FolderEmptyException | DestinationNotFoundException
                 | IllegalArgumentException | IllegalStateException e) {
            logger.warn("Falha de validacao ao iniciar backup: {}", e.getMessage());
            securityAuditService.recordFailure("backup.start", "backup_request", "validation_failed",
                    Map.of("sourceCount", sources.size(), "destinationCount", destinations.size()));
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao iniciar backup", e);
            securityAuditService.recordFailure("backup.start", "backup_request", "internal_error",
                    Map.of("sourceCount", sources.size(), "destinationCount", destinations.size()));
            return internalErrorResponse(
                    "Erro interno ao iniciar backup.",
                    "backup_start_failed",
                    Map.of("sourceCount", sources.size(), "destinationCount", destinations.size()),
                    BACKUP_START_PATH
            );
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

            return ResponseEntity.ok(CollectionResponse.of(responseList));

        } catch (Exception e) {
            logger.error("Erro ao listar historico de backups", e);
            return internalErrorResponse(
                    "Erro interno ao listar historico.",
                    "backup_history_list_failed",
                    null,
                    BACKUP_HISTORY_PATH
            );
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
                        .body(ApiErrorResponse.of(
                                HttpStatus.BAD_REQUEST,
                                "Data inicial nao pode ser posterior a data final.",
                                "invalid_date_range",
                                Map.of("startDate", startDate, "endDate", endDate),
                                BACKUP_HISTORY_SEARCH_PATH
                        ));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(invalidRangeResponse(
                                "page_size_out_of_range",
                                "Tamanho da pagina deve estar entre 1 e 100.",
                                "size",
                                size,
                                BACKUP_HISTORY_SEARCH_PATH
                        ));
            }

            if (page < 0) {
                return ResponseEntity.badRequest()
                        .body(invalidMinimumResponse(
                                "page_index_out_of_range",
                                "Indice da pagina nao pode ser negativo.",
                                "page",
                                page,
                                0,
                                BACKUP_HISTORY_SEARCH_PATH
                        ));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
            Page<BackupResponse> result = historyService.searchHistory(status, startDate, endDate, pageable);

            return ResponseEntity.ok(PageResponse.from(result));

        } catch (Exception e) {
            logger.error("Erro ao buscar historico", e);
            return internalErrorResponse(
                    "Erro interno ao buscar historico.",
                    "backup_history_search_failed",
                    null,
                    BACKUP_HISTORY_SEARCH_PATH
            );
        }
    }

    @GetMapping("/history/stats")
    public ResponseEntity<Object> getStatistics() {
        try {
            BackupStatsResponse stats = historyService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Erro ao calcular estatisticas", e);
            return internalErrorResponse(
                    "Erro interno ao calcular estatisticas.",
                    "backup_statistics_failed",
                    null,
                    BACKUP_HISTORY_STATS_PATH
            );
        }
    }

    @GetMapping("/history/recent")
    public ResponseEntity<Object> getRecentBackups(
            @RequestParam(defaultValue = "5") int limit
    ) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(invalidRangeResponse(
                                "limit_out_of_range",
                                "Limite deve estar entre 1 e 100.",
                                "limit",
                                limit,
                                BACKUP_HISTORY_RECENT_PATH
                        ));
            }

            List<BackupResponse> recent = historyService.getRecentBackups(limit);
            return ResponseEntity.ok(CollectionResponse.of(recent));

        } catch (Exception e) {
            logger.error("Erro ao buscar backups recentes", e);
            return internalErrorResponse(
                    "Erro interno ao buscar backups recentes.",
                    "backup_recent_list_failed",
                    Map.of("limit", limit),
                    BACKUP_HISTORY_RECENT_PATH
            );
        }
    }

    @GetMapping("/progress")
    public SseEmitter streamProgress() {
        return progressEmitter.createEmitter();
    }

    @PostMapping("/{taskId}/pause")
    public ResponseEntity<Object> pauseBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.pauseBackup(taskId);
            if (success) {
                securityAuditService.recordSuccess("backup.pause", "backup_task", Map.of("taskId", taskId));
                return successResponse("Backup pausado com sucesso", taskId);
            } else {
                securityAuditService.recordFailure("backup.pause", "backup_task", "task_not_found_or_invalid",
                        Map.of("taskId", taskId));
                return backupTaskNotFoundResponse(taskId, "pause", "Tarefa nao encontrada ou nao pode ser pausada.");
            }
        } catch (Exception e) {
            logger.error("Erro ao pausar backup {}", taskId, e);
            securityAuditService.recordFailure("backup.pause", "backup_task", "internal_error", Map.of("taskId", taskId));
            return internalTaskErrorResponse("Erro interno ao pausar backup.", taskId, "/api/backup/" + taskId + "/pause");
        }
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<Object> resumeBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.resumeBackup(taskId);
            if (success) {
                securityAuditService.recordSuccess("backup.resume", "backup_task", Map.of("taskId", taskId));
                return successResponse("Backup retomado com sucesso", taskId);
            } else {
                securityAuditService.recordFailure("backup.resume", "backup_task", "task_not_found_or_invalid",
                        Map.of("taskId", taskId));
                return backupTaskNotFoundResponse(taskId, "resume", "Tarefa nao encontrada ou nao pode ser retomada.");
            }
        } catch (Exception e) {
            logger.error("Erro ao retomar backup {}", taskId, e);
            securityAuditService.recordFailure("backup.resume", "backup_task", "internal_error", Map.of("taskId", taskId));
            return internalTaskErrorResponse("Erro interno ao retomar backup.", taskId, "/api/backup/" + taskId + "/resume");
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Object> cancelBackup(@PathVariable Long taskId) {
        try {
            boolean success = backupService.cancelBackup(taskId);
            if (success) {
                securityAuditService.recordSuccess("backup.cancel", "backup_task", Map.of("taskId", taskId));
                return successResponse("Backup cancelado com sucesso", taskId);
            } else {
                securityAuditService.recordFailure("backup.cancel", "backup_task", "task_not_found_or_invalid",
                        Map.of("taskId", taskId));
                return backupTaskNotFoundResponse(taskId, "cancel", "Tarefa nao encontrada ou nao pode ser cancelada.");
            }
        } catch (Exception e) {
            logger.error("Erro ao cancelar backup {}", taskId, e);
            securityAuditService.recordFailure("backup.cancel", "backup_task", "internal_error", Map.of("taskId", taskId));
            return internalTaskErrorResponse("Erro interno ao cancelar backup.", taskId, "/api/backup/" + taskId + "/cancel");
        }
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<Object> getTaskStatus(@PathVariable Long taskId) {
        Optional<BackupTask> task = backupRepository.findById(taskId);
        if (task.isPresent()) {
            return ResponseEntity.ok(BackupTaskSummaryResponse.fromTask(task.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(
                            HttpStatus.NOT_FOUND,
                            "Tarefa de backup nao encontrada.",
                            "backup_task_not_found",
                            Map.of("taskId", taskId),
                            "/api/backup/" + taskId + "/status",
                            taskId
                    ));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<CollectionResponse<BackupTaskSummaryResponse>> getActiveTasks() {
        // Busca apenas os status operacionais necessarios para evitar filtrar tudo em memoria.
        List<BackupTaskSummaryResponse> activeTasks = backupRepository.findByStatusIn(List.of(Status.EM_ANDAMENTO, Status.PAUSADO))
                .stream()
                .map(BackupTaskSummaryResponse::fromTask)
                .toList();

        return ResponseEntity.ok(CollectionResponse.of(activeTasks));
    }

    private ResponseEntity<Object> internalErrorResponse(String message, String code, Object details, String path) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        message,
                        code,
                        details,
                        path
                )
        );
    }

    private ResponseEntity<Object> successResponse(String message, Long taskId) {
        return ResponseEntity.ok(OperationResponse.success(message, taskId));
    }

    private ApiErrorResponse invalidRangeResponse(String code,
                                                  String message,
                                                  String field,
                                                  int value,
                                                  String path) {
        return ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                message,
                code,
                Map.of(field, value, "min", 1, "max", 100),
                path
        );
    }

    private ApiErrorResponse invalidMinimumResponse(String code,
                                                    String message,
                                                    String field,
                                                    int value,
                                                    int min,
                                                    String path) {
        return ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                message,
                code,
                Map.of(field, value, "min", min),
                path
        );
    }

    private ResponseEntity<Object> backupTaskNotFoundResponse(Long taskId, String action, String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND,
                        message,
                        "backup_task_not_found_or_invalid",
                        Map.of("taskId", taskId, "action", action),
                        "/api/backup/" + taskId + "/" + action,
                        taskId
                )
        );
    }

    private ResponseEntity<Object> internalTaskErrorResponse(String message, Long taskId, String path) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        message,
                        "backup_task_operation_failed",
                        Map.of("taskId", taskId),
                        path,
                        taskId
                )
        );
    }

    private ConflictDetails conflictDetails(String source, String destination, Long taskId) {
        return new ConflictDetails(source, destination, taskId);
    }

    private record ConflictDetails(String source, String destination, Long taskId) {
    }
}
