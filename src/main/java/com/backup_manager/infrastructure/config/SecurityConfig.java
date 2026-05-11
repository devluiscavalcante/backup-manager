package com.backup_manager.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppSecurityProperties securityProperties) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health/application").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").hasRole(securityProperties.getRole())
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(AppSecurityProperties securityProperties,
                                                 PasswordEncoder passwordEncoder) {
        validateDefaultPassword(
                securityProperties.getPassword(),
                securityProperties.isAllowDefaultPassword(),
                "APP_SECURITY_PASSWORD",
                "Usando senha default de seguranca para o usuario administrador."
        );

        List<UserDetails> users = new ArrayList<>();

        UserDetails adminUser = User.withUsername(securityProperties.getUsername())
                .password(passwordEncoder.encode(securityProperties.getPassword()))
                .roles(securityProperties.getRole())
                .build();
        users.add(adminUser);

        if (securityProperties.isOperatorEnabled()) {
            if (securityProperties.getUsername().equalsIgnoreCase(securityProperties.getOperatorUsername())) {
                throw new IllegalStateException("APP_SECURITY_OPERATOR_USERNAME deve ser diferente do usuario administrador.");
            }

            validateDefaultPassword(
                    securityProperties.getOperatorPassword(),
                    securityProperties.isAllowDefaultPassword(),
                    "APP_SECURITY_OPERATOR_PASSWORD",
                    "Usando senha default de seguranca para o usuario operador."
            );

            UserDetails operatorUser = User.withUsername(securityProperties.getOperatorUsername())
                    .password(passwordEncoder.encode(securityProperties.getOperatorPassword()))
                    .roles(securityProperties.getOperatorRole())
                    .build();
            users.add(operatorUser);
        }

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private void validateDefaultPassword(String configuredPassword,
                                         boolean allowDefaultPassword,
                                         String environmentVariable,
                                         String warningMessage) {
        if (!"change-me-now".equals(configuredPassword) && !"change-me-operator".equals(configuredPassword)) {
            return;
        }

        if (!allowDefaultPassword) {
            throw new IllegalStateException(environmentVariable
                    + " precisa ser definido fora do valor default para subir a aplicacao neste ambiente.");
        }

        logger.warn("{} Defina {} no ambiente.", warningMessage, environmentVariable);
    }
}
