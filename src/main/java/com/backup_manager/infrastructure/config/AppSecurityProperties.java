package com.backup_manager.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String username = "admin";
    private String password = "change-me-now";
    private String role = "ADMIN";
}
