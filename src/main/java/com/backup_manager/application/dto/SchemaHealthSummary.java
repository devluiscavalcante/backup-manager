package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SchemaHealthSummary {

    private final boolean healthy;
    private final String currentVersion;
    private final Integer appliedMigrations;
    private final boolean orphanedHistory;
    private final List<String> missingManagedTables;

    public static SchemaHealthSummary from(SchemaStatusResponse schemaStatus) {
        return new SchemaHealthSummary(
                schemaStatus.isHealthy(),
                schemaStatus.getCurrentVersion(),
                schemaStatus.getAppliedMigrations(),
                schemaStatus.isOrphanedHistory(),
                schemaStatus.getMissingManagedTables()
        );
    }
}
