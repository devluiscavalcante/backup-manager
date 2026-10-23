package com.backup_manager.infrastructure.logging;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.storage.BackupMetadata;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LogService {

    // O warnings.log acumula todas as execucoes do destino e nunca e truncado: a consulta devolve so o
    // final (os alertas recentes) em vez de carregar o arquivo inteiro na memoria e na resposta.
    static final int MAX_LOG_BYTES = 256 * 1024;

    private final BackupRepository backupRepository;

    private final List<Path> basePaths;

    public LogService(
            BackupRepository backupRepository,
            @Value("${logs.base-paths:}") String configuredBasePaths
    ) {
        this.backupRepository = backupRepository;
        this.basePaths = parseBasePaths(configuredBasePaths);
    }

    public String readLog(Path logPath) throws IOException {
        if (!Files.exists(logPath)) {
            return "O arquivo log ainda nao foi gerado em " + logPath;
        }
        long size = Files.size(logPath);
        String content = size <= MAX_LOG_BYTES
                ? new String(Files.readAllBytes(logPath), StandardCharsets.UTF_8)
                : readTail(logPath, size);
        return content.isBlank()
                ? "Nenhum alerta encontrado - Backup concluido"
                : content;
    }

    private static String readTail(Path logPath, long size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_LOG_BYTES);
        try (SeekableByteChannel channel = Files.newByteChannel(logPath, StandardOpenOption.READ)) {
            channel.position(size - MAX_LOG_BYTES);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // le ate encher o buffer ou acabar o arquivo
            }
        }
        String tail = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        // O corte cai no meio de uma linha (ou de um caractere multibyte): descarta ate a primeira quebra.
        int firstBreak = tail.indexOf('\n');
        String completeLines = firstBreak >= 0 ? tail.substring(firstBreak + 1) : tail;
        return String.format("... alertas anteriores omitidos: exibindo os ultimos %d KB de %d KB do log.%n",
                MAX_LOG_BYTES / 1024, size / 1024) + completeLines;
    }

    public Path resolveLatestWarningsLog() throws IOException {

        Optional<BackupTask> lastOk = backupRepository.findTopByStatusOrderByFinishedAtDesc(Status.CONCLUIDO);
        if (lastOk.isPresent()) {
            Path fromDb = BackupMetadata.locateWarningsLog(Path.of(lastOk.get().getDestinationPath()));
            if (Files.exists(fromDb)) return fromDb;
        }

        Optional<BackupTask> lastAny = backupRepository.findTopByOrderByFinishedAtDesc();
        if (lastAny.isPresent()) {
            Path fromDbAny = BackupMetadata.locateWarningsLog(Path.of(lastAny.get().getDestinationPath()));
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
        try (var walk = Files.walk(base, 5)) {
            return walk
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("warnings.log"))
                    .max(Comparator.comparing(this::safeLastModified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private FileTime safeLastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
