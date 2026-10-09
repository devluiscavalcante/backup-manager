package com.backup_manager.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileStorageOperations {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageOperations.class);

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

        Files.walkFileTree(realSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!callback.shouldContinue()) return FileVisitResult.TERMINATE;

                if (shouldExclude(dir, realSource, excludedFolders)) {
                    callback.onWarning("Diretório ignorado", dir);
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
                    if (isIncrementalNeeded(file, targetFile, attrs)) {
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

    private boolean isIncrementalNeeded(Path source, Path target, BasicFileAttributes sourceAttrs) throws IOException {
        if (!Files.exists(target)) return true;
        BasicFileAttributes targetAttrs = Files.readAttributes(target, BasicFileAttributes.class);
        return sourceAttrs.size() != targetAttrs.size() ||
                sourceAttrs.lastModifiedTime().toMillis() > targetAttrs.lastModifiedTime().toMillis();
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
