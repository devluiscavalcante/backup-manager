package com.backup_manager.infrastructure.logging;

import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
