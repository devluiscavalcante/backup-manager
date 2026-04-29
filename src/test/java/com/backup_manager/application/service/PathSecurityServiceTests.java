package com.backup_manager.application.service;

import com.backup_manager.infrastructure.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathSecurityServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowPathInsideConfiguredRoot() {
        PathSecurityService service = createService(List.of(tempDir.toString()));
        Path allowedFile = tempDir.resolve("documents").resolve("backup.zip");

        assertDoesNotThrow(() -> service.validateManagedPath(allowedFile.toString(), "backup"));
    }

    @Test
    void shouldBlockPathOutsideConfiguredRoot() {
        PathSecurityService service = createService(List.of(tempDir.toString()));
        Path outsidePath = tempDir.getParent().resolve("fora-da-allowlist");

        assertThrows(SecurityException.class,
                () -> service.validateManagedPath(outsidePath.toString(), "backup"));
    }

    @Test
    void shouldBlockPathTraversalAttempt() {
        PathSecurityService service = createService(List.of(tempDir.toString()));
        String traversalPath = tempDir.resolve("docs").resolve("..").resolve("segredo").toString();

        assertThrows(SecurityException.class,
                () -> service.validateManagedPath(traversalPath, "backup"));
    }

    @Test
    void shouldFallbackToUserHomeWhenAllowlistIsEmpty() {
        PathSecurityService service = createService(List.of());
        Path homePath = Paths.get(System.getProperty("user.home")).resolve("backup-test");

        assertDoesNotThrow(() -> service.validateManagedPath(homePath.toString(), "backup"));
    }

    private PathSecurityService createService(List<String> allowedRoots) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setAllowedPathRoots(allowedRoots);
        return new PathSecurityService(properties);
    }
}
