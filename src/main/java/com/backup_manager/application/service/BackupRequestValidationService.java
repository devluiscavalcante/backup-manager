package com.backup_manager.application.service;

import com.backup_manager.domain.model.OneTimeBackupEntity;
import com.backup_manager.domain.model.OneTimeBackupStatus;
import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.domain.service.BackupManager;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.OneTimeBackupRepository;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import com.backup_manager.infrastructure.storage.BackupSnapshots;
import com.backup_manager.infrastructure.storage.FileStorageOperations;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackupRequestValidationService {

    private final BackupManager backupManager;
    private final PathSecurityService pathSecurityService;
    private final FileStorageOperations storageOps;
    private final BackupRepository backupRepository;
    private final ScheduledBackupRepository scheduledBackupRepository;
    private final OneTimeBackupRepository oneTimeBackupRepository;

    public BackupRequestValidationService(BackupManager backupManager,
                                          PathSecurityService pathSecurityService,
                                          FileStorageOperations storageOps,
                                          BackupRepository backupRepository,
                                          ScheduledBackupRepository scheduledBackupRepository,
                                          OneTimeBackupRepository oneTimeBackupRepository) {
        this.backupManager = backupManager;
        this.pathSecurityService = pathSecurityService;
        this.storageOps = storageOps;
        this.backupRepository = backupRepository;
        this.scheduledBackupRepository = scheduledBackupRepository;
        this.oneTimeBackupRepository = oneTimeBackupRepository;
    }

    public void validateSchedulableRequest(List<String> sources, List<String> destinations) {
        validateSchedulableRequest(null, sources, destinations);
    }

    /** scheduleId e o agendamento sendo editado, que nao conflita consigo mesmo (nulo nos demais casos). */
    public void validateSchedulableRequest(Long scheduleId, List<String> sources, List<String> destinations) {
        validatePathPolicy(sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");

            backupManager.validateSource(sourcePath.toString());
            validateDestinationRoot(destinationPath);
        }

        validateDestinationsNotShared(scheduleId, sources, destinations);
    }

    // Cada origem precisa de uma arvore de destino propria: duas origens no mesmo destino sobrescreviam os
    // arquivos uma da outra e, com snapshots, a retencao de uma apagaria os snapshots da outra. Fica fora de
    // validatePathPolicy de proposito: agendamentos antigos que ja compartilham destino continuam carregados
    // no boot, mas a execucao deles para aqui com a explicacao.
    private void validateDestinationsNotShared(Long scheduleId, List<String> sources, List<String> destinations) {
        Map<Path, Path> requested = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");
            if (requested.putIfAbsent(destinationPath, sourcePath) != null) {
                throw new IllegalArgumentException(String.format(
                        "O destino %s foi informado mais de uma vez. Cada origem precisa de um destino proprio.",
                        destinationPath));
            }
        }

        List<ScheduledBackupEntity> schedules = scheduledBackupRepository.findAll();
        List<OneTimeBackupEntity> pending =
                oneTimeBackupRepository.findByStatusOrderByScheduledForAsc(OneTimeBackupStatus.PENDENTE);

        requested.forEach((destinationPath, sourcePath) -> {
            for (ScheduledBackupEntity schedule : schedules) {
                if (!schedule.getId().equals(scheduleId)) {
                    rejectIfOtherSource(schedule.getSources(), schedule.getDestinations(), sourcePath,
                            destinationPath, "pelo agendamento '" + schedule.getName() + "'");
                }
            }
            for (OneTimeBackupEntity oneTime : pending) {
                rejectIfOtherSource(oneTime.getSources(), oneTime.getDestinations(), sourcePath,
                        destinationPath, "pelo backup unico '" + oneTime.getName() + "'");
            }

            backupRepository.findFirstByDestinationPathAndSourcePathNotAndSnapshotPathIsNotNullAndSnapshotRemovedAtIsNullOrderByIdDesc(
                    destinationPath.toString(), sourcePath.toString()
            ).ifPresent(owner -> {
                throw new IllegalArgumentException(String.format(
                        "O destino %s ja guarda snapshots da origem %s. Cada origem precisa de um destino proprio.",
                        destinationPath, owner.getSourcePath()));
            });
        });
    }

    private void rejectIfOtherSource(List<String> sources, List<String> destinations, Path sourcePath,
                                     Path destinationPath, String owner) {
        if (sources == null || destinations == null) {
            return;
        }
        for (int i = 0; i < Math.min(sources.size(), destinations.size()); i++) {
            Path otherDestination = normalize(destinations.get(i));
            Path otherSource = normalize(sources.get(i));
            if (destinationPath.equals(otherDestination) && !sourcePath.equals(otherSource)) {
                throw new IllegalArgumentException(String.format(
                        "O destino %s ja e usado %s para a origem %s. Cada origem precisa de um destino proprio.",
                        destinationPath, owner, otherSource));
            }
        }
    }

    // Caminho salvo pode ter saido da allowlist ou estar num disco desmontado: compara pela forma normalizada.
    private Path normalize(String raw) {
        try {
            return pathSecurityService.validateManagedPath(raw, "backup");
        } catch (RuntimeException e) {
            return Paths.get(raw).toAbsolutePath().normalize();
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
        validateSchedulableRequest(null, sources, destinations);

        for (int i = 0; i < sources.size(); i++) {
            Path sourcePath = pathSecurityService.validateManagedPath(sources.get(i), "backup");
            Path destinationPath = pathSecurityService.validateManagedPath(destinations.get(i), "backup");
            validateAvailableSpace(sourcePath.toFile(), destinationPath);
        }
    }

    // O que nao mudou desde o ultimo snapshot vira hardlink e nao ocupa espaco, entao exigir o tamanho
    // inteiro da origem recusava backups que cabiam com folga. A estimativa e o que falta copiar, arquivo a
    // arquivo: descontar o tamanho total do destino zerava a conta quando ele tinha outros arquivos. Sem
    // hardlinks (FAT/exFAT) cada snapshot e uma copia completa.
    long estimateRequiredBytes(File sourceFolder, Path destinationPath) {
        try {
            if (Files.isDirectory(destinationPath) && !FileStorageOperations.supportsHardLinks(destinationPath)) {
                return backupManager.scan(sourceFolder).totalBytes();
            }
            Path reference = Files.isDirectory(destinationPath)
                    ? BackupSnapshots.referenceFor(destinationPath)
                    : destinationPath;
            return storageOps.estimatePendingBytes(sourceFolder.toPath(), reference);
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
