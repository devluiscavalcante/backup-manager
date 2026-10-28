package com.backup_manager.application.service;

import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.infrastructure.config.AppSecurityProperties;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(
                        List.of(tempDir.resolve("source").toString()),
                        List.of()
                )
        );

        assertEquals("As listas de origens e destinos nao podem estar vazias.", exception.getMessage());
    }

    @Test
    void shouldRejectUnsafePathsForScheduledRequest() {
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        SecurityException exception = assertThrows(SecurityException.class, () ->
                service.validateSchedulableRequest(
                        List.of(tempDir.getParent().resolve("outside-source").toString()),
                        List.of(tempDir.resolve("destination").toString())
                )
        );

        assertEquals(
                "O caminho informado para a operacao de backup nao pertence a uma raiz permitida.",
                exception.getMessage()
        );
    }

    @Test
    void pathPolicyShouldAcceptUnavailableSourceAndDestinationInsideAllowlist() {
        // Disco externo desmontado no boot: o agendamento precisa continuar registrado.
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertDoesNotThrow(() ->
                service.validatePathPolicy(
                        List.of(tempDir.resolve("missing-source").toString()),
                        List.of(tempDir.resolve("missing-destination").toString())
                )
        );
    }

    @Test
    void pathPolicyShouldStillRejectPathsOutsideAllowlistAndOverlaps() {
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertThrows(SecurityException.class, () ->
                service.validatePathPolicy(
                        List.of(tempDir.getParent().resolve("outside-source").toString()),
                        List.of(tempDir.resolve("destination").toString())
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePathPolicy(
                        List.of(tempDir.resolve("source").toString()),
                        List.of(tempDir.resolve("source").resolve("backup").toString())
                )
        );
    }

    @Test
    void shouldRejectEmptySourceDirectory() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("empty-source"));
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        FolderEmptyException exception = assertThrows(FolderEmptyException.class, () ->
                service.validateSchedulableRequest(
                        List.of(sourceDir.toString()),
                        List.of(destinationDir.toString())
                )
        );

        assertEquals("A pasta de origem esta vazia.", exception.getMessage());
    }

    @Test
    void shouldRejectDifferentSourceAndDestinationCountsWhenBothListsArePresent() {
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(
                        List.of(tempDir.resolve("source-a").toString(), tempDir.resolve("source-b").toString()),
                        List.of(tempDir.resolve("destination").toString())
                )
        );

        assertEquals("O numero de origens deve ser igual ao numero de destinos.", exception.getMessage());
    }

    @Test
    void shouldRejectDestinationInsideSource() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.writeString(sourceDir.resolve("file.txt"), "content");
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(
                        List.of(sourceDir.toString()),
                        List.of(sourceDir.resolve("backup").toString())
                )
        );

        assertEquals("Origem e destino nao podem estar um dentro do outro na operacao de backup.",
                exception.getMessage());
    }

    @Test
    void shouldRejectSourceInsideDestinationOrSamePath() throws IOException {
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        Path sourceDir = Files.createDirectories(destinationDir.resolve("source"));
        Files.writeString(sourceDir.resolve("file.txt"), "content");
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(List.of(sourceDir.toString()), List.of(destinationDir.toString())));
        assertThrows(IllegalArgumentException.class, () ->
                service.validateSchedulableRequest(List.of(sourceDir.toString()), List.of(sourceDir.toString())));
    }

    @Test
    void shouldAllowSiblingDestinationSharingNamePrefix() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(sourceDir.resolve("file.txt"), "content");
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertDoesNotThrow(() ->
                service.validateSchedulableRequest(
                        List.of(sourceDir.toString()),
                        List.of(tempDir.resolve("data-backup").toString())
                )
        );
    }

    @Test
    void requiredSpaceShouldDiscountWhatIsAlreadyInTheDestination() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.write(sourceDir.resolve("a.bin"), new byte[3000]);
        Files.write(sourceDir.resolve("b.bin"), new byte[1000]);
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        Files.copy(sourceDir.resolve("a.bin"), destinationDir.resolve("a.bin"), StandardCopyOption.COPY_ATTRIBUTES);
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertEquals(1000, service.estimateRequiredBytes(sourceDir.toFile(), destinationDir));
        assertEquals(4000, service.estimateRequiredBytes(sourceDir.toFile(), tempDir.resolve("novo-destino")));
    }

    @Test
    void requiredSpaceShouldIgnoreDestinationFilesThatDoNotComeFromTheSource() throws IOException {
        // 50 "GB" de outros arquivos no destino nao podem zerar a estimativa de uma origem de 40.
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.write(sourceDir.resolve("a.bin"), new byte[4000]);
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        Files.write(destinationDir.resolve("antigo.bin"), new byte[5000]);
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertEquals(4000, service.estimateRequiredBytes(sourceDir.toFile(), destinationDir));
    }

    @Test
    void requiredSpaceShouldCountFilesThatChangedInTheSource() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.write(sourceDir.resolve("a.bin"), new byte[2000]);
        Path destinationDir = Files.createDirectories(tempDir.resolve("destination"));
        Files.write(destinationDir.resolve("a.bin"), new byte[1500]);
        BackupRequestValidationService service = createService(List.of(tempDir.toString()));

        assertEquals(2000, service.estimateRequiredBytes(sourceDir.toFile(), destinationDir));
    }

    @Test
    void availableSpaceShouldComeFromTheDestinationFileStoreEvenWhenItDoesNotExistYet() throws IOException {
        Path missingDestination = tempDir.resolve("novo").resolve("destino");

        assertEquals(Files.getFileStore(tempDir), BackupRequestValidationService.destinationStore(missingDestination));
        assertEquals(Files.getFileStore(tempDir), BackupRequestValidationService.destinationStore(tempDir));
    }

    private BackupRequestValidationService createService(List<String> allowedRoots) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setAllowedPathRoots(allowedRoots);

        PathSecurityService pathSecurityService = new PathSecurityService(properties);
        return new BackupRequestValidationService(new BackupManager(), pathSecurityService, new FileStorageOperations());
    }
}
