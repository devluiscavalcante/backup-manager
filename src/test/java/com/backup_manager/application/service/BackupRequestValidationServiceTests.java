package com.backup_manager.application.service;

import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.infrastructure.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupRequestValidationServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowValidExecutableRequest() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.writeString(sourceDir.resolve("file.txt"), "content");
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));

        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertDoesNotThrow(() ->
                service.validateExecutableRequest(
                        List.of(sourceDir.toString()),
                        List.of(destinationDir.toString())
                )
        );
    }

    @Test
    void shouldRejectMismatchedSourceAndDestinationCounts() {
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(
                        List.of(tempDir.resolve("source").toString()),
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectUnsafePathsForScheduledRequest() {
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertThrows(SecurityException.class, () ->
                service.validateSchedulableRequest(
                        List.of(tempDir.getParent().resolve("outside-source").toString()),
                        List.of(tempDir.resolve("destination").toString())
                )
        );
    }

    @Test
    void shouldRejectEmptySourceDirectory() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("empty-source"));
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertThrows(RuntimeException.class, () ->
                service.validateSchedulableRequest(
                        List.of(sourceDir.toString()),
                        List.of(destinationDir.toString())
                )
        );
    }

    private BackupRequestValidationService createService(List<String> allowedRoots) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setAllowedPathRoots(allowedRoots);

        PathSecurityService pathSecurityService = new PathSecurityService(properties);
        return new BackupRequestValidationService(new BackupManager(), pathSecurityService);
    }
}
