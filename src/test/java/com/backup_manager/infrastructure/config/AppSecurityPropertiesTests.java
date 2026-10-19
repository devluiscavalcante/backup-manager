package com.backup_manager.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AppSecurityPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void refusesToStartWithBlankAdminPassword() {
        // Reproduz um .env copiado do .env.example sem preencher APP_SECURITY_PASSWORD.
        contextRunner.withPropertyValues(
                        "APP_SECURITY_PASSWORD=",
                        "app.security.password=${APP_SECURITY_PASSWORD:change-me-now}")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWithConfiguredAdminPassword() {
        contextRunner.withPropertyValues("app.security.password=s3nha-definida")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration
    @EnableConfigurationProperties(AppSecurityProperties.class)
    static class PropertiesConfig {
    }
}
