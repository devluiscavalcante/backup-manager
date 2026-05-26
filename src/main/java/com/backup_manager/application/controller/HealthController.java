package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApplicationHealthResponse;
import com.backup_manager.application.dto.DatabaseHealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/database")
    public ResponseEntity<DatabaseHealthResponse> checkDatabase() {
        try {
            // Mantemos o health de banco minimo para evitar fingerprinting do ambiente.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            return ResponseEntity.ok(new DatabaseHealthResponse(
                    "UP",
                    "PostgreSQL",
                    null,
                    LocalDateTime.now()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(503).body(new DatabaseHealthResponse(
                    "DOWN",
                    "PostgreSQL",
                    "Falha na verificacao de conectividade.",
                    LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/application")
    public ResponseEntity<ApplicationHealthResponse> checkApplication() {
        return ResponseEntity.ok(new ApplicationHealthResponse(
                "UP",
                "Backup Manager",
                LocalDateTime.now(),
                "1.0.0"
        ));
    }
}
