package com.backup_manager.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);
    private static final Set<String> MANAGED_TABLES = Set.of(
            "backup_tasks",
            "scheduled_backups",
            "scheduled_backup_sources",
            "scheduled_backup_destinations",
            "restore_tasks",
            "security_audit_events"
    );

    @Bean
    @ConditionalOnMissingBean
    Flyway flyway(DataSource dataSource, Environment environment) {
        String defaultSchema = environment.getProperty("spring.flyway.default-schema");
        String[] schemas = environment.getProperty("spring.flyway.schemas", String[].class);
        String historyTable = environment.getProperty("spring.flyway.table");

        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(resolveLocations(environment))
                .outOfOrder(environment.getProperty("spring.flyway.out-of-order", Boolean.class, false))
                .baselineOnMigrate(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false));

        String baselineVersion = environment.getProperty("spring.flyway.baseline-version");
        if (baselineVersion != null && !baselineVersion.isBlank()) {
            configuration.baselineVersion(MigrationVersion.fromVersion(baselineVersion));
        }

        if (defaultSchema != null && !defaultSchema.isBlank()) {
            configuration.defaultSchema(defaultSchema);
        }

        if (schemas != null && schemas.length > 0) {
            configuration.schemas(schemas);
        }

        if (historyTable != null && !historyTable.isBlank()) {
            configuration.table(historyTable);
        }

        Flyway flyway = configuration.load();
        String targetSchema = resolveSchema(defaultSchema, schemas);
        String targetHistoryTable = resolveHistoryTable(historyTable);

        repairOrphanedFlywayHistory(dataSource, targetSchema, targetHistoryTable);
        // Spring Boot 4.0.1 in this runtime does not auto-run Flyway, so we migrate explicitly.
        flyway.migrate();
        validateManagedTables(dataSource, targetSchema, targetHistoryTable);
        return flyway;
    }

    @Bean
    static EntityManagerFactoryDependsOnFlywayPostProcessor entityManagerFactoryDependsOnFlyway() {
        return new EntityManagerFactoryDependsOnFlywayPostProcessor();
    }

    private String[] resolveLocations(Environment environment) {
        String[] configuredLocations = environment.getProperty("spring.flyway.locations", String[].class);
        return (configuredLocations == null || configuredLocations.length == 0)
                ? new String[]{"classpath:db/migration"}
                : Arrays.stream(configuredLocations)
                .filter(location -> location != null && !location.isBlank())
                .toArray(String[]::new);
    }

    private String resolveSchema(String defaultSchema, String[] schemas) {
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            return defaultSchema;
        }

        if (schemas != null && schemas.length > 0 && schemas[0] != null && !schemas[0].isBlank()) {
            return schemas[0];
        }

        return "public";
    }

    private String resolveHistoryTable(String configuredHistoryTable) {
        return (configuredHistoryTable == null || configuredHistoryTable.isBlank())
                ? "flyway_schema_history"
                : configuredHistoryTable;
    }

    private void repairOrphanedFlywayHistory(DataSource dataSource, String schema, String historyTable) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> tables = existingTables(connection, schema, historyTable);

            if (tables.size() == 1 && tables.contains(historyTable)) {
                logger.warn("Detected orphaned Flyway history in schema '{}'. Rebuilding migration history.", schema);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE " + qualifiedIdentifier(schema, historyTable));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Flyway schema state before migration.", e);
        }
    }

    private void validateManagedTables(DataSource dataSource, String schema, String historyTable) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> tables = existingTables(connection, schema, historyTable);
            Set<String> missingTables = new LinkedHashSet<>(MANAGED_TABLES);
            missingTables.removeAll(tables);

            if (!missingTables.isEmpty()) {
                throw new IllegalStateException(
                        ("Flyway migration completed, but schema '%s' is still missing managed tables: %s. " +
                                "Recreate the schema or restore the missing tables before starting the application.")
                                        .formatted(schema, missingTables)
                );
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to validate managed Flyway tables after migration.", e);
        }
    }

    private Set<String> existingTables(Connection connection, String schema, String historyTable) throws Exception {
        Set<String> tables = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                """)) {
            statement.setString(1, schema);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    if (MANAGED_TABLES.contains(tableName) || historyTable.equals(tableName)) {
                        tables.add(tableName);
                    }
                }
            }
        }

        return tables;
    }

    private String qualifiedIdentifier(String schema, String table) {
        return quotedIdentifier(schema) + "." + quotedIdentifier(table);
    }

    private String quotedIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static final class EntityManagerFactoryDependsOnFlywayPostProcessor
            extends AbstractDependsOnBeanFactoryPostProcessor {

        EntityManagerFactoryDependsOnFlywayPostProcessor() {
            super(EntityManagerFactory.class, "flyway");
        }
    }
}
