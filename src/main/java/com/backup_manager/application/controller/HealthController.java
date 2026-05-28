package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApplicationHealthSummary;
import com.backup_manager.application.dto.HealthStatusResponse;
import com.backup_manager.application.dto.SchemaHealthSummary;
import com.backup_manager.application.dto.SchemaStatusResponse;
import com.backup_manager.application.service.SchemaDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final SchemaDiagnosticsService schemaDiagnosticsService;

    public HealthController(JdbcTemplate jdbcTemplate, SchemaDiagnosticsService schemaDiagnosticsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaDiagnosticsService = schemaDiagnosticsService;
    }

    @GetMapping("/database")
    public ResponseEntity<HealthStatusResponse> checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            SchemaStatusResponse schemaStatus = schemaDiagnosticsService.inspect();
            SchemaHealthSummary details = SchemaHealthSummary.from(schemaStatus);

            if (!schemaStatus.isHealthy()) {
                return ResponseEntity.status(503)
                        .body(HealthStatusResponse.of(
                                "DOWN",
                                "PostgreSQL",
                                null,
                                "Schema inconsistente.",
                                details
                        ));
            }

            return ResponseEntity.ok(HealthStatusResponse.of("UP", "PostgreSQL", null, null, details));

        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(HealthStatusResponse.of("DOWN", "PostgreSQL", null, "Falha na verificacao de conectividade."));
        }
    }

    @GetMapping("/application")
    public ResponseEntity<HealthStatusResponse> checkApplication() {
        return ResponseEntity.ok(HealthStatusResponse.of(
                "UP",
                "Backup Manager",
                "1.0.0",
                null,
                ApplicationHealthSummary.current()
        ));
    }
}
