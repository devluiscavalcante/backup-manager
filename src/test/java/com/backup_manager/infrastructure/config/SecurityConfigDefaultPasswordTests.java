package com.backup_manager.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigDefaultPasswordTests {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @ParameterizedTest
    @ValueSource(strings = {
            "change-me-now", "change-me-operator", "troque-esta-senha", "uma-senha-forte", "senha-do-operador"
    })
    void refusesPublishedPlaceholderAdminPassword(String password) {
        AppSecurityProperties properties = properties(password);

        assertThatThrownBy(() -> securityConfig.userDetailsService(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_PASSWORD");
    }

    @Test
    void refusesPublishedPlaceholderOperatorPassword() {
        AppSecurityProperties properties = properties("admin-secret-123");
        properties.setOperatorEnabled(true);
        properties.setOperatorPassword("senha-do-operador");

        assertThatThrownBy(() -> securityConfig.userDetailsService(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_OPERATOR_PASSWORD");
    }

    @Test
    void allowsPlaceholderWhenExplicitlyEnabled() {
        AppSecurityProperties properties = properties("uma-senha-forte");
        properties.setAllowDefaultPassword(true);

        assertThatCode(() -> securityConfig.userDetailsService(properties, passwordEncoder))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsCustomPassword() {
        assertThatCode(() -> securityConfig.userDetailsService(properties("admin-secret-123"), passwordEncoder))
                .doesNotThrowAnyException();
    }

    private static AppSecurityProperties properties(String password) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setPassword(password);
        return properties;
    }
}
