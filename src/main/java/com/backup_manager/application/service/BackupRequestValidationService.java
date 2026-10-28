package com.backup_manager.application.service;

import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class BackupRequestValidationService {

    private final BackupManager backupManager;
    private final PathSecurityService pathSecurityService;
    private final FileStorageOperations storageOps;

    public BackupRequestValidationService(BackupManager backupManager,
                                          PathSecurityService pathSecurityService,
                                          FileStorageOperations storageOps) {
        this.backupManager = backupManager;
        this.pathSecurityService = pathSecurityService;
        this.storageOps = storageOps;
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
    // inteiro da origem recusava backups que cabiam com folga. A estimativa e o que falta copiar, arquivo a
    // arquivo: descontar o tamanho total do destino zerava a conta quando ele tinha outros arquivos.
    long estimateRequiredBytes(File sourceFolder, Path destinationPath) {
        try {
            return storageOps.estimatePendingBytes(sourceFolder.toPath(), destinationPath);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel estimar o tamanho da origem " + sourceFolder, e);
        }
    }

    // O espaco livre e o do volume onde o destino fica, nao o da raiz do caminho: no Docker/Linux a raiz e
    // "/" e o destino costuma ser outro volume montado. Se o destino ainda nao existe, vale o do ancestral
    // existente mais proximo, que e onde ele sera criado.
    static FileStore destinationStore(Path destinationPath) throws IOException {
        Path existing = destinationPath.toAbsolutePath();
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new IllegalStateException("O disco de destino " + destinationPath.getRoot() + " nao esta acessivel.");
        }
        return Files.getFileStore(existing);
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
        FileStore store;
        long availableSpace;
        try {
            store = destinationStore(destinationPath);
            availableSpace = store.getUsableSpace();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel consultar o espaco livre do destino " + destinationPath, e);
        }
        long requiredSpace = estimateRequiredBytes(sourceFolder, destinationPath);

        if (requiredSpace > availableSpace) {
            String error = String.format(
                    "Espaco insuficiente no volume %s do destino %s. Necessario: %d MB, Disponivel: %d MB",
                    store,
                    destinationPath,
                    requiredSpace / (1024 * 1024),
                    availableSpace / (1024 * 1024)
            );
            throw new IllegalStateException(error);
        }
    }
}
