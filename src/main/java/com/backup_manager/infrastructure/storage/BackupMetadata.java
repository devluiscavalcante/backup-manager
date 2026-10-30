package com.backup_manager.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Arquivos que o proprio backup grava no destino. Ficam numa pasta reservada para nao disputar nome com o
 * conteudo copiado: um warnings.log na raiz da origem era sobrescrito pelo log e nunca restaurado.
 */
public final class BackupMetadata {

    public static final String DIRECTORY = ".backup-manager";
    static final String LOGS_DIRECTORY = "logs";

    private BackupMetadata() {
    }

    public static Path warningsLog(Path backupRoot) {
        return backupRoot.resolve(DIRECTORY).resolve(FileStorageOperations.WARNINGS_LOG);
    }

    /**
     * Garante a pasta de metadados e devolve o log de alertas. Num destino do formato antigo (sem a pasta),
     * o warnings.log da raiz foi gerado pelo backup: vai para a pasta e o historico de alertas e mantido.
     */
    public static Path prepareWarningsLog(Path backupRoot) throws IOException {
        Path directory = backupRoot.resolve(DIRECTORY);
        Path log = directory.resolve(FileStorageOperations.WARNINGS_LOG);
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
            Path legacyLog = legacyWarningsLog(backupRoot);
            if (Files.isRegularFile(legacyLog) && !Files.exists(log)) {
                Files.move(legacyLog, log);
            }
        }
        return log;
    }

    /**
     * Log de alertas de uma execucao. Fica fora do snapshot para sobreviver a uma execucao que falhou (cujo
     * snapshot e descartado) e leva o nome do snapshot, entao a ordem alfabetica e a cronologica.
     */
    public static Path runWarningsLog(Path backupRoot, Path snapshot) {
        return backupRoot.resolve(DIRECTORY).resolve(LOGS_DIRECTORY)
                .resolve(snapshot.getFileName() + "." + FileStorageOperations.WARNINGS_LOG);
    }

    /**
     * Mantem so os maxFiles logs de execucao mais recentes do destino, sem nunca remover keep (o da execucao
     * atual). Antes cada destino tinha um unico warnings.log que crescia sem limite.
     */
    public static void pruneRunLogs(Path backupRoot, Path keep, int maxFiles) throws IOException {
        Path directory = backupRoot.resolve(DIRECTORY).resolve(LOGS_DIRECTORY);
        if (maxFiles <= 0 || !Files.isDirectory(directory)) {
            return;
        }
        List<Path> logs;
        try (Stream<Path> entries = Files.list(directory)) {
            logs = entries
                    .filter(path -> path.getFileName().toString().endsWith("." + FileStorageOperations.WARNINGS_LOG))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = maxFiles; i < logs.size(); i++) {
            if (!logs.get(i).equals(keep)) {
                Files.deleteIfExists(logs.get(i));
            }
        }
    }

    /** Log de alertas do backup, no local atual ou, em backups antigos, na raiz. */
    public static Path locateWarningsLog(Path backupRoot) {
        Path log = warningsLog(backupRoot);
        return Files.exists(log) || hasMetadataDirectory(backupRoot) ? log : legacyWarningsLog(backupRoot);
    }

    /**
     * Entrada do backup que nao e conteudo do usuario: a pasta de metadados e o que esta dentro dela, ou o
     * warnings.log da raiz quando o backup ainda e do formato antigo.
     */
    public static boolean isMetadata(Path path, Path backupRoot) {
        Path directory = backupRoot.resolve(DIRECTORY);
        if (path.startsWith(directory)) {
            return true;
        }
        return backupRoot.equals(path.getParent())
                && path.getFileName().toString().equalsIgnoreCase(FileStorageOperations.WARNINGS_LOG)
                && !hasMetadataDirectory(backupRoot);
    }

    /** Pasta de metadados de outro backup na raiz da origem (origem que e destino de outro backup). */
    static boolean isMetadataDirectoryOf(Path dir, Path sourceRoot) {
        return sourceRoot.equals(dir.getParent())
                && dir.getFileName().toString().equalsIgnoreCase(DIRECTORY);
    }

    private static boolean hasMetadataDirectory(Path backupRoot) {
        return Files.isDirectory(backupRoot.resolve(DIRECTORY));
    }

    private static Path legacyWarningsLog(Path backupRoot) {
        return backupRoot.resolve(FileStorageOperations.WARNINGS_LOG);
    }
}
