package com.backup_manager.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApplicationVersionService {

    private static final String DEVELOPMENT_VERSION = "development";

    private final String configuredVersion;

    public ApplicationVersionService(@Value("${app.version:}") String configuredVersion) {
        this.configuredVersion = configuredVersion;
    }

    public String currentVersion() {
        if (configuredVersion != null && !configuredVersion.isBlank()) {
            return configuredVersion;
        }

        String implementationVersion = ApplicationVersionService.class
                .getPackage()
                .getImplementationVersion();

        return implementationVersion == null || implementationVersion.isBlank()
                ? DEVELOPMENT_VERSION
                : implementationVersion;
    }
}
