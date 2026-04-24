package com.backup_manager.application.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/database")
    public ResponseEntity<?> checkDatabase() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Mantemos o health de banco minimo para evitar fingerprinting do ambiente.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            response.put("status", "UP");
            response.put("database", "PostgreSQL");
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("database", "PostgreSQL");
            response.put("error", "Falha na verificacao de conectividade.");
            response.put("timestamp", java.time.LocalDateTime.now());
            return ResponseEntity.status(503).body(response);
        }
    }

    @GetMapping("/application")
    public ResponseEntity<?> checkApplication() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Backup Manager");
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }
}
