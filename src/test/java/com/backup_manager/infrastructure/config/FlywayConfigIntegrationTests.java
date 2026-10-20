package com.backup_manager.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.allow-default-password=true",
        "app.security.password=admin-secret",
        "app.security.operator-enabled=true",
        "app.security.operator-password=operator-secret"
})
@ActiveProfiles("test")
class FlywayConfigIntegrationTests {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    private String schemaName;

    @Test
    void shouldRebuildSchemaWhenOnlyFlywayHistoryTableRemains() throws Exception {
        schemaName = "orphan_flyway_history_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
        }

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .outOfOrder(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + schemaName + ".security_audit_events CASCADE");
            statement.execute("DROP TABLE " + schemaName + ".restore_tasks CASCADE");
            statement.execute("DROP TABLE " + schemaName + ".scheduled_backup_destinations CASCADE");
            statement.execute("DROP TABLE " + schemaName + ".scheduled_backup_sources CASCADE");
            statement.execute("DROP TABLE " + schemaName + ".scheduled_backups CASCADE");
            statement.execute("DROP TABLE " + schemaName + ".backup_tasks CASCADE");
        }

        assertThat(existingTables(schemaName)).containsExactly("flyway_schema_history");

        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.flyway.locations", "classpath:db/migration")
                .withProperty("spring.flyway.schemas", schemaName)
                .withProperty("spring.flyway.default-schema", schemaName)
                .withProperty("spring.flyway.out-of-order", "true");

        new FlywayConfig().flyway(dataSource, environment);

        assertThat(existingTables(schemaName)).contains(
                "backup_tasks",
                "scheduled_backups",
                "scheduled_backup_sources",
                "scheduled_backup_destinations",
                "restore_tasks",
                "security_audit_events",
                "flyway_schema_history"
        );
    }

    @Test
    void shouldFailWithClearMessageWhenManagedTablesAreMissingAfterMigration() throws Exception {
        schemaName = "broken_flyway_schema_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
        }

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .outOfOrder(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + schemaName + ".backup_tasks CASCADE");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.flyway.locations", "classpath:db/migration")
                .withProperty("spring.flyway.schemas", schemaName)
                .withProperty("spring.flyway.default-schema", schemaName)
                .withProperty("spring.flyway.out-of-order", "true");

        assertThatThrownBy(() -> new FlywayConfig().flyway(dataSource, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema '" + schemaName + "' is still missing managed tables")
                .hasMessageContaining("backup_tasks");
    }

    @Test
    void shouldBaselineLegacySchemaWithoutFlagsWhenItBelongsToThisApplication() throws Exception {
        schemaName = "legacy_auto_baseline_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schemaName,
                "CREATE TABLE " + schemaName + ".scheduled_backup_entity_sources ("
                        + "scheduled_backup_entity_id BIGINT NOT NULL, source_path VARCHAR(1000))");

        new FlywayConfig().flyway(dataSource(), strictEnvironment());

        assertThat(appliedVersions(schemaName)).contains("0", "1", "1.1", "2", "4", "5", "6");
    }

    @Test
    void shouldRefuseUnrelatedNonEmptySchemaWithoutHistory() throws Exception {
        schemaName = "foreign_schema_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schemaName,
                "CREATE TABLE " + schemaName + ".clientes (id BIGSERIAL PRIMARY KEY)");

        assertThatThrownBy(() -> new FlywayConfig().flyway(dataSource(), strictEnvironment()))
                .isInstanceOf(FlywayException.class);
        assertThat(existingTables(schemaName)).containsExactly("clientes");
    }

    @Test
    void shouldApplyLegacyV11OutOfOrderWithoutFlags() throws Exception {
        schemaName = "legacy_gap_" + UUID.randomUUID().toString().replace("-", "");
        migrateFreshSchemaThenForget("1.1");

        new FlywayConfig().flyway(dataSource(), strictEnvironment());

        assertThat(appliedVersions(schemaName)).contains("1.1");
    }

    @Test
    void shouldFailOnUnexpectedOutOfOrderMigration() throws Exception {
        schemaName = "unexpected_gap_" + UUID.randomUUID().toString().replace("-", "");
        migrateFreshSchemaThenForget("2");

        assertThatThrownBy(() -> new FlywayConfig().flyway(dataSource(), strictEnvironment()))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("2");
    }

    // Simula um banco que nunca recebeu a migracao indicada, embora tenha versoes posteriores.
    private void migrateFreshSchemaThenForget(String version) throws Exception {
        execute("CREATE SCHEMA " + schemaName);
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .load()
                .migrate();
        execute("DELETE FROM " + schemaName + ".flyway_schema_history WHERE version = '" + version + "'");
    }

    private MockEnvironment strictEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.flyway.locations", "classpath:db/migration")
                .withProperty("spring.flyway.schemas", schemaName)
                .withProperty("spring.flyway.default-schema", schemaName);
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(jdbcUrl, username, password);
    }

    private void execute(String... sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            for (String command : sql) {
                statement.execute(command);
            }
        }
    }

    private Set<String> appliedVersions(String schema) throws Exception {
        Set<String> versions = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version FROM " + schema + ".flyway_schema_history WHERE success = true")) {
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
        }
        return versions;
    }

    @AfterEach
    void cleanupSchema() throws Exception {
        if (schemaName == null) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    private Set<String> existingTables(String schema) throws Exception {
        Set<String> tables = new HashSet<>();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement("""
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
}
