package com.backup_manager.application.service;

import com.backup_manager.application.dto.SchemaStatusResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class SchemaDiagnosticsService {

    private static final Set<String> MANAGED_TABLES = Set.of(
            "backup_tasks",
            "scheduled_backups",
            "scheduled_backup_sources",
            "scheduled_backup_destinations",
            "restore_tasks",
            "security_audit_events"
    );

    private final DataSource dataSource;
    private final Environment environment;

    public SchemaDiagnosticsService(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    public SchemaStatusResponse inspect() {
        String schema = resolveSchema();
        String historyTable = resolveHistoryTable();

        try (Connection connection = dataSource.getConnection()) {
            List<String> existingTables = existingTables(connection, schema);
            Set<String> missingManagedTables = new LinkedHashSet<>(MANAGED_TABLES);
            missingManagedTables.removeAll(existingTables);

            boolean historyPresent = existingTables.contains(historyTable);
            int managedCount = 0;
            for (String table : existingTables) {
                if (MANAGED_TABLES.contains(table)) {
                    managedCount++;
                }
            }

            return SchemaStatusResponse.of(
                    missingManagedTables.isEmpty(),
                    historyPresent,
                    historyPresent && managedCount == 0,
                    schema,
                    historyTable,
                    currentVersion(connection, schema, historyTable),
                    appliedMigrations(connection, schema, historyTable),
                    existingTables,
                    new ArrayList<>(missingManagedTables)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Flyway schema diagnostics.", e);
        }
    }

    private List<String> existingTables(Connection connection, String schema) throws Exception {
        List<String> tables = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                ORDER BY table_name
                """)) {
            statement.setString(1, schema);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("table_name"));
                }
            }
        }

        return tables;
    }

    private String currentVersion(Connection connection, String schema, String historyTable) throws Exception {
        if (!historyTableExists(connection, schema, historyTable)) {
            return null;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version
                FROM %s
                WHERE success = true
                ORDER BY installed_rank DESC
                LIMIT 1
                """.formatted(qualifiedIdentifier(schema, historyTable)));
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString("version") : null;
        }
    }

    private Integer appliedMigrations(Connection connection, String schema, String historyTable) throws Exception {
        if (!historyTableExists(connection, schema, historyTable)) {
            return 0;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM %s
                WHERE success = true
                """.formatted(qualifiedIdentifier(schema, historyTable)));
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private boolean historyTableExists(Connection connection, String schema, String historyTable) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """)) {
            statement.setString(1, schema);
            statement.setString(2, historyTable);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private String resolveSchema() {
        String defaultSchema = environment.getProperty("spring.flyway.default-schema");
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            return defaultSchema;
        }

        String[] schemas = environment.getProperty("spring.flyway.schemas", String[].class);
        if (schemas != null && schemas.length > 0 && schemas[0] != null && !schemas[0].isBlank()) {
            return schemas[0];
        }

        return "public";
    }

    private String resolveHistoryTable() {
        String historyTable = environment.getProperty("spring.flyway.table");
        return (historyTable == null || historyTable.isBlank()) ? "flyway_schema_history" : historyTable;
    }

    private String qualifiedIdentifier(String schema, String table) {
        return quotedIdentifier(schema) + "." + quotedIdentifier(table);
    }

    private String quotedIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
