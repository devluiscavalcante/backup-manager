package com.backup_manager.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    @NotBlank
    private String username = "admin";

    @NotBlank
    private String password = "change-me-now";

    @NotBlank
    private String role = "ADMIN";

    private boolean allowDefaultPassword = false;

    private List<String> allowedPathRoots = new ArrayList<>();
}
