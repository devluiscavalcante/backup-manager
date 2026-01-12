package com.backup_manager.infrastructure.logging;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import io.jsonwebtoken.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LogService {

    private final BackupRepository backupRepository;

    private final List<Path> basePaths;

    public LogService(
            BackupRepository backupRepository,
            @Value("${logs.base-paths:}") String configuredBasePaths
    ) {
        this.backupRepository = backupRepository;
        this.basePaths = parseBasePaths(configuredBasePaths);
    }

    public String redLog(Path logPath) throws IOException, java.io.IOException {
        if (!Files.exists(logPath)) {
            return "O arquivo log ainda não foi gerado em " + logPath;
        }
        String content = Files.readString(logPath);
        return content.isBlank()
                ? "Nenhum alerta encontrado - Backup concluído"
                : content;
    }

    public Path resolveLatestWarningsLog() throws IOException {

        Optional<BackupTask> lastOk = backupRepository.findTopByStatusOrderByFinishedAtDesc(Status.CONCLUIDO);
        if (lastOk.isPresent()) {
            Path fromDb = Path.of(lastOk.get().getDestinationPath(), "warnings.log");
            if (Files.exists(fromDb)) return fromDb;
        }

        Optional<BackupTask> lastAny = backupRepository.findTopByOrderByFinishedAtDesc();
        if (lastAny.isPresent()) {
            Path fromDbAny = Path.of(lastAny.get().getDestinationPath(), "warnings.log");
            if (Files.exists(fromDbAny)) return fromDbAny;
        }

        Path found = scanBasesForLatestWarnings();
        if (found != null) return found;

        throw new IOException("Nenhum warnings.log encontrado. Ajuste 'logs.base-paths' ou execute um backup.");
    }

    private static List<Path> parseBasePaths(String cfg) {
        List<Path> list = new ArrayList<>();
        if (cfg == null || cfg.isBlank()) return list;
        for (String raw : cfg.split(";")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                list.add(Path.of(trimmed));
            }
        }
        return list;
    }

    private Path scanBasesForLatestWarnings() {

        return basePaths.stream()
                .map(this::latestWarningsUnderBase)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(this::safeLastModified))
                .orElse(null);
    }

    private Optional<Path> latestWarningsUnderBase(Path base) {
        if (!Files.exists(base)) return Optional.empty();
        try {
            return Files.walk(base, 5)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("warnings.log"))
                    .max(Comparator.comparing(this::safeLastModified));
        } catch (IOException | java.io.IOException e) {
            return Optional.empty();
        }
    }

    private java.nio.file.attribute.FileTime safeLastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException | java.io.IOException e) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }
}