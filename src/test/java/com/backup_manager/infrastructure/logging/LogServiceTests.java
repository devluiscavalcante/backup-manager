package com.backup_manager.infrastructure.logging;

import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LogServiceTests {

    private final BackupRepository backupRepository = mock(BackupRepository.class);
    private final LogService logService = new LogService(backupRepository, "");

    @Test
    void readLogShouldReturnAsciiMessageWhenFileDoesNotExist() throws Exception {
        Path missingLog = Path.of("C:/temp/missing-warnings.log");

        String content = logService.readLog(missingLog);

        assertEquals("O arquivo log ainda nao foi gerado em " + missingLog, content);
    }

    @Test
    void readLogShouldReturnAsciiMessageWhenFileIsBlank() throws Exception {
        Path logFile = Files.createTempFile("warnings", ".log");
        try {
            Files.writeString(logFile, "");

            String content = logService.readLog(logFile);

            assertEquals("Nenhum alerta encontrado - Backup concluido", content);
        } finally {
            Files.deleteIfExists(logFile);
        }
    }

    @Test
    void readLogShouldReturnOnlyTheRecentTailOfALargeLog(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("warnings.log");
        StringBuilder log = new StringBuilder();
        int line = 0;
        while (log.length() < LogService.MAX_LOG_BYTES * 3) {
            log.append("[2026-10-23T10:00:00] [WARNING] Diretório ignorado: C:/dados/pasta-").append(line++).append('\n');
        }
        Files.writeString(logFile, log.toString(), StandardCharsets.UTF_8);

        String content = logService.readLog(logFile);

        assertTrue(content.startsWith("... alertas anteriores omitidos"));
        assertTrue(content.length() <= LogService.MAX_LOG_BYTES + 200);
        // Os alertas mais recentes ficam; os antigos saem.
        assertTrue(content.contains("pasta-" + (line - 1) + "\n"));
        assertFalse(content.contains("pasta-0\n"));
        // Nenhuma linha cortada no meio depois do aviso.
        String firstLogLine = content.lines().skip(1).findFirst().orElseThrow();
        assertTrue(firstLogLine.startsWith("[2026-10-23T10:00:00] [WARNING]"));
    }

    @Test
    void readLogShouldReturnSmallLogsUntouched(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("warnings.log");
        Files.writeString(logFile, "[2026-10-23T10:00:00] [WARNING] Link simbolico ignorado: C:/dados/link\n");

        assertEquals("[2026-10-23T10:00:00] [WARNING] Link simbolico ignorado: C:/dados/link\n",
                logService.readLog(logFile));
    }
}
