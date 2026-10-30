package com.backup_manager.application.service;

import com.backup_manager.domain.event.BackupCompletedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.config.BackupRetentionProperties;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.BackupSnapshots;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Remove os snapshots que sairam da janela de retencao de um destino. Roda no proprio worker, logo apos a
 * conclusao, para que o proximo backup do destino nao encontre a limpeza pela metade.
 */
@Service
public class BackupRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(BackupRetentionService.class);

    private final BackupRetentionProperties properties;
    private final BackupRepository backupRepository;

    public BackupRetentionService(BackupRetentionProperties properties, BackupRepository backupRepository) {
        this.properties = properties;
        this.backupRepository = backupRepository;
    }

    // Falha na limpeza nao pode virar falha de um backup que ja terminou: so registra e segue.
    @EventListener
    public void onBackupCompleted(BackupCompletedEvent event) {
        BackupTask task = event.getTask();
        if (task.getSnapshotPath() == null) {
            return;
        }
        try {
            apply(Paths.get(task.getDestinationPath()), Paths.get(task.getSnapshotPath()), LocalDateTime.now());
        } catch (Exception e) {
            logger.warn("Retencao de snapshots falhou em {}: {}", task.getDestinationPath(), e.getMessage(), e);
        }
    }

    public List<Path> apply(Path destinationRoot, Path keep, LocalDateTime now) throws IOException {
        List<Path> expired = selectExpired(BackupSnapshots.completed(destinationRoot), keep,
                properties.getMaxSnapshots(), properties.getMaxDays(), now);
        List<Path> removed = new ArrayList<>();
        for (Path snapshot : expired) {
            try {
                BackupSnapshots.deleteTree(snapshot);
                removed.add(snapshot);
                logger.info("Snapshot removido pela retencao: {}", snapshot);
            } catch (IOException e) {
                logger.warn("Nao foi possivel remover o snapshot {}: {}", snapshot, e.getMessage());
            }
        }
        markRemoved(removed, now);
        return removed;
    }

    /**
     * Snapshots fora da janela, recebidos do mais recente para o mais antigo. O mais recente e o que acabou de
     * ser gravado nunca saem, mesmo com limites que excluiriam tudo.
     */
    static List<Path> selectExpired(List<Path> newestFirst, Path keep, int maxSnapshots, int maxDays,
                                    LocalDateTime now) {
        List<Path> expired = new ArrayList<>();
        LocalDateTime oldestAllowed = maxDays > 0 ? now.minusDays(maxDays) : null;
        for (int i = 1; i < newestFirst.size(); i++) {
            Path snapshot = newestFirst.get(i);
            if (snapshot.equals(keep)) {
                continue;
            }
            boolean overCount = maxSnapshots > 0 && i >= maxSnapshots;
            boolean tooOld = oldestAllowed != null && isOlderThan(snapshot, oldestAllowed);
            if (overCount || tooOld) {
                expired.add(snapshot);
            }
        }
        return expired;
    }

    private static boolean isOlderThan(Path snapshot, LocalDateTime limit) {
        try {
            return BackupSnapshots.startedAt(snapshot).isBefore(limit);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    // A entrada continua no historico; o restore dela passa a explicar que o snapshot expirou.
    private void markRemoved(List<Path> removed, LocalDateTime now) {
        if (removed.isEmpty()) {
            return;
        }
        List<BackupTask> tasks = backupRepository.findBySnapshotPathIn(removed.stream().map(Path::toString).toList());
        tasks.forEach(task -> task.setSnapshotRemovedAt(now));
        backupRepository.saveAll(tasks);
    }
}
