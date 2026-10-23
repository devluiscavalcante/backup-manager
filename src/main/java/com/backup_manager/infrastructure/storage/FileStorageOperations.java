package com.backup_manager.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileStorageOperations {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageOperations.class);

    /** Alertas da copia, gravados na pasta de metadados do destino (veja {@link BackupMetadata}). */
    public static final String WARNINGS_LOG = "warnings.log";

    // FAT32 grava a data de modificacao com resolucao de 2 s (exFAT, 10 ms): mesmo com COPY_ATTRIBUTES o
    // horario no destino nunca bate exatamente com o da origem. Mesma tolerancia do --modify-window do rsync.
    static final long FAT_MODIFY_WINDOW_MILLIS = 2_000;
    private static final Set<String> FAT_FILE_STORE_TYPES = Set.of(
            "fat", "fat12", "fat16", "fat32", "exfat", "vfat", "msdos"
    );

    public interface BackupProgressCallback {
        void onFileProcessed(Path file, BasicFileAttributes attrs);
        void onWarning(String message, Path path);
        boolean shouldContinue();
    }

    public int copyDirectoryIncremental(Path source, Path destination, List<String> excludedFolders,
                                        BackupProgressCallback callback) throws IOException {
        AtomicInteger warnings = new AtomicInteger(0);
        // A varredura parte do caminho real para que links internos possam ser detectados e ignorados.
        Path realSource = source.toRealPath();
        long modifyWindowMillis = modifyWindowFor(destination);

        Files.walkFileTree(realSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!callback.shouldContinue()) return FileVisitResult.TERMINATE;

                if (shouldExclude(dir, realSource, excludedFolders)) {
                    callback.onWarning("Diretório ignorado", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                if (BackupMetadata.isMetadataDirectoryOf(dir, realSource)) {
                    // A origem e destino de outro backup: os alertas dele nao se misturam aos deste.
                    callback.onWarning("Pasta de metadados de outro backup ignorada", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                if (!dir.equals(realSource) && FileLinks.isRedirected(dir)) {
                    callback.onWarning("Link simbolico/junction ignorado", dir);
                    warnings.incrementAndGet();
                    return FileVisitResult.SKIP_SUBTREE;
                }

                try {
                    Path targetDir = destination.resolve(realSource.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                } catch (IOException e) {
                    callback.onWarning("Erro ao criar diretório", dir);
                    warnings.incrementAndGet();
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!callback.shouldContinue()) return FileVisitResult.TERMINATE;

                if (shouldExclude(file, realSource, excludedFolders)) return FileVisitResult.CONTINUE;

                if (FileLinks.isLink(attrs)) {
                    callback.onWarning("Link simbolico ignorado", file);
                    warnings.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                Path targetFile = destination.resolve(realSource.relativize(file));
                try {
                    if (isIncrementalNeeded(targetFile, attrs, modifyWindowMillis)) {
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    callback.onFileProcessed(file, attrs);
                } catch (IOException e) {
                    callback.onWarning("Erro ao processar arquivo: " + e.getMessage(), file);
                    warnings.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Sem acesso a raiz nao ha o que copiar: propaga para o backup falhar explicitamente.
                if (file.equals(realSource)) {
                    throw exc;
                }

                callback.onWarning("Falha ao acessar: " + exc.getMessage(), file);
                warnings.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });

        return warnings.get();
    }

    private boolean isIncrementalNeeded(Path target, BasicFileAttributes sourceAttrs, long modifyWindowMillis)
            throws IOException {
        if (!Files.exists(target)) return true;
        BasicFileAttributes targetAttrs = Files.readAttributes(target, BasicFileAttributes.class);
        return isIncrementalNeeded(sourceAttrs.size(), sourceAttrs.lastModifiedTime().toMillis(),
                targetAttrs.size(), targetAttrs.lastModifiedTime().toMillis(), modifyWindowMillis);
    }

    // Qualquer diferenca de mtime alem da precisao do destino conta: um arquivo revertido para uma versao
    // antiga, com o mesmo tamanho, tambem precisa ser recopiado.
    static boolean isIncrementalNeeded(long sourceSize, long sourceMtimeMillis,
                                       long targetSize, long targetMtimeMillis, long modifyWindowMillis) {
        return sourceSize != targetSize || Math.abs(sourceMtimeMillis - targetMtimeMillis) > modifyWindowMillis;
    }

    private static long modifyWindowFor(Path destination) {
        try {
            return modifyWindowFor(Files.getFileStore(destination).type());
        } catch (IOException | SecurityException e) {
            // Sem saber o sistema de arquivos, fica a comparacao exata (no pior caso recopia, nunca pula).
            return 0;
        }
    }

    static long modifyWindowFor(String fileStoreType) {
        return fileStoreType != null && FAT_FILE_STORE_TYPES.contains(fileStoreType.toLowerCase(Locale.ROOT))
                ? FAT_MODIFY_WINDOW_MILLIS
                : 0;
    }

    // Compara apenas o nome da entrada (sem diferenciar maiusculas). A raiz da origem nunca e excluida,
    // mesmo que algum diretorio do seu caminho tenha um nome presente na lista de exclusoes.
    static boolean shouldExclude(Path path, Path sourceRoot, List<String> excludedNames) {
        if (excludedNames == null || excludedNames.isEmpty() || path.equals(sourceRoot)) {
            return false;
        }

        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }

        String name = fileName.toString();
        return excludedNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(name::equalsIgnoreCase);
    }
}
