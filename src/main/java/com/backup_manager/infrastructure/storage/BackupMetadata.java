package com.backup_manager.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Arquivos que o proprio backup grava no destino. Ficam numa pasta reservada para nao disputar nome com o
 * conteudo copiado: um warnings.log na raiz da origem era sobrescrito pelo log e nunca restaurado.
 */
public final class BackupMetadata {

    public static final String DIRECTORY = ".backup-manager";

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
