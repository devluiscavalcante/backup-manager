package com.backup_manager.application.controller;

import com.backup_manager.application.dto.HealthStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/database")
    public ResponseEntity<HealthStatusResponse> checkDatabase() {
        try {
            // Mantemos o health de banco minimo para evitar fingerprinting do ambiente.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            return ResponseEntity.ok(HealthStatusResponse.of("UP", "PostgreSQL", null, null));

        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(HealthStatusResponse.of("DOWN", "PostgreSQL", null, "Falha na verificacao de conectividade."));
        }
    }

    @GetMapping("/application")
    public ResponseEntity<HealthStatusResponse> checkApplication() {
        return ResponseEntity.ok(HealthStatusResponse.of("UP", "Backup Manager", "1.0.0", null));
    }
}
