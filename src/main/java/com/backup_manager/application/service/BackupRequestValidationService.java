package com.backup_manager.application.service;

import com.backup_manager.domain.service.BackupManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Service
public class BackupRequestValidationService {

    private final BackupManager backupManager;
    private final PathSecurityService pathSecurityService;

    public BackupRequestValidationService(BackupManager backupManager,
                                          PathSecurityService pathSecurityService) {
        this.backupManager = backupManager;
        this.pathSecurityService = pathSecurityService;
    }

    public void validateSchedulableRequest(List<String> sources, List<String> destinations) {
        validatePathPolicy(sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");

            backupManager.validateSource(sourcePath.toString());
            validateDestinationRoot(destinationPath);
        }
    }

    // Apenas regras estaticas (pareamento, allowlist, sobreposicao): um disco externo desmontado no boot
    // nao pode descartar o agendamento; a disponibilidade e verificada na hora de executar.
    public void validatePathPolicy(List<String> sources, List<String> destinations) {
        validatePairing(sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");

            pathSecurityService.ensureNotOverlapping(sourcePath, destinationPath, "backup");
        }
    }

    public void validateExecutableRequest(List<String> sources, List<String> destinations) {
        validateSchedulableRequest(sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");
            validateAvailableSpace(sourcePath.toFile(), destinationPath);
        }
    }

    // Backup incremental: o que ja esta no destino nao sera copiado de novo, entao exigir o tamanho
    // inteiro da origem recusava backups que cabiam com folga. A estimativa e o que falta copiar.
    long estimateRequiredBytes(File sourceFolder, Path destinationPath) {
        long sourceBytes = backupManager.scan(sourceFolder).totalBytes();
        File destination = destinationPath.toFile();
        long alreadyCopied = destination.isDirectory() ? backupManager.scan(destination).totalBytes() : 0;
        return Math.max(0, sourceBytes - alreadyCopied);
    }

    public void validatePairing(List<String> sources, List<String> destinations) {
        if (sources == null || destinations == null || sources.isEmpty() || destinations.isEmpty()) {
            throw new IllegalArgumentException("As listas de origens e destinos nao podem estar vazias.");
        }

        if (sources.size() != destinations.size()) {
            throw new IllegalArgumentException("O numero de origens deve ser igual ao numero de destinos.");
        }
    }

    private void validateDestinationRoot(Path destinationPath) {
        if (destinationPath.getRoot() == null) {
            throw new IllegalStateException("O caminho de destino informado nao possui uma raiz acessivel.");
        }

        File destinationRoot = destinationPath.getRoot().toFile();
        if (!destinationRoot.exists()) {
            throw new IllegalStateException("O disco de destino " + destinationPath.getRoot() + " nao esta acessivel.");
        }
    }

    private void validateAvailableSpace(File sourceFolder, Path destinationPath) {
        File destinationRoot = destinationPath.getRoot().toFile();
        long requiredSpace = estimateRequiredBytes(sourceFolder, destinationPath);
        long availableSpace = destinationRoot.getUsableSpace();

        if (requiredSpace > availableSpace) {
            String error = String.format(
                    "Espaco insuficiente no disco %s. Necessario: %d MB, Disponivel: %d MB",
                    destinationPath.getRoot(),
                    requiredSpace / (1024 * 1024),
                    availableSpace / (1024 * 1024)
            );
            throw new IllegalStateException(error);
        }
    }
}
