package com.backup_manager.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Bean
    @ConditionalOnMissingBean
    Flyway flyway(DataSource dataSource, Environment environment) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(resolveLocations(environment))
                .outOfOrder(environment.getProperty("spring.flyway.out-of-order", Boolean.class, false))
                .baselineOnMigrate(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false));

        String baselineVersion = environment.getProperty("spring.flyway.baseline-version");
        if (baselineVersion != null && !baselineVersion.isBlank()) {
            configuration.baselineVersion(MigrationVersion.fromVersion(baselineVersion));
        }

        String defaultSchema = environment.getProperty("spring.flyway.default-schema");
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            configuration.defaultSchema(defaultSchema);
        }

        String[] schemas = environment.getProperty("spring.flyway.schemas", String[].class);
        if (schemas != null && schemas.length > 0) {
            configuration.schemas(schemas);
        }

        String table = environment.getProperty("spring.flyway.table");
        if (table != null && !table.isBlank()) {
            configuration.table(table);
        }

        Flyway flyway = configuration.load();
        // Spring Boot 4.0.1 in this runtime does not auto-run Flyway, so we migrate explicitly.
        flyway.migrate();
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

    static final class EntityManagerFactoryDependsOnFlywayPostProcessor
            extends AbstractDependsOnBeanFactoryPostProcessor {

        EntityManagerFactoryDependsOnFlywayPostProcessor() {
            super(EntityManagerFactory.class, "flyway");
        }
    }
}
