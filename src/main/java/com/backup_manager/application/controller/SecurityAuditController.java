package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.PageResponse;
import com.backup_manager.application.dto.SecurityAuditEventResponse;
import com.backup_manager.application.service.SecurityAuditQueryService;
import com.backup_manager.domain.model.AuditOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system/audit")
public class SecurityAuditController {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditController.class);

    private final SecurityAuditQueryService securityAuditQueryService;

    public SecurityAuditController(SecurityAuditQueryService securityAuditQueryService) {
        this.securityAuditQueryService = securityAuditQueryService;
    }

    @GetMapping
    public ResponseEntity<Object> searchAuditEvents(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
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

            PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<SecurityAuditEventResponse> result = securityAuditQueryService.search(
                    action,
                    outcome,
                    actor,
                    requestId,
                    startDate,
                    endDate,
                    pageable
            );

            return ResponseEntity.ok(PageResponse.from(result));
        } catch (Exception e) {
            logger.error("Erro ao consultar auditoria de seguranca", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao consultar auditoria de seguranca."));
        }
    }
}
