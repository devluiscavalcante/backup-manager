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
            // Testa conexão
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // Verifica tabela backup_tasks
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'backup_tasks'",
                    Integer.class
            );

            // Conta registros
            Integer recordCount = 0;
            if (tableCount > 0) {
                recordCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM backup_tasks",
                        Integer.class
                );
            }

            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);

            response.put("status", "UP");
            response.put("database", "PostgreSQL");
            response.put("version", version.split(",")[0]);
            response.put("connectionTest", "SUCCESS");
            response.put("tableExists", tableCount > 0);
            response.put("totalRecords", recordCount);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("database", "PostgreSQL");
            response.put("error", e.getMessage());
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