package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class SchemaStatusResponse {

    private final boolean healthy;
    private final boolean flywayHistoryPresent;
    private final boolean orphanedHistory;
    private final String schema;
    private final String historyTable;
    private final String currentVersion;
    private final Integer appliedMigrations;
    private final List<String> existingTables;
    private final List<String> missingManagedTables;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static SchemaStatusResponse of(boolean healthy,
                                          boolean flywayHistoryPresent,
                                          boolean orphanedHistory,
                                          String schema,
                                          String historyTable,
                                          String currentVersion,
                                          Integer appliedMigrations,
                                          List<String> existingTables,
                                          List<String> missingManagedTables) {
        return new SchemaStatusResponse(
                healthy,
                flywayHistoryPresent,
                orphanedHistory,
                schema,
                historyTable,
                currentVersion,
                appliedMigrations,
                existingTables,
                missingManagedTables,
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }
}
