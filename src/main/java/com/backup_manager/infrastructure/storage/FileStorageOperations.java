package com.backup_manager.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
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
        Path logFile = destination.resolve("warnings.log");

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!callback.shouldContinue()) return FileVisitResult.TERMINATE;

                if (shouldExclude(dir, excludedFolders)) {
                    callback.onWarning("Diretório ignorado", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                try {
                    Path targetDir = destination.resolve(source.relativize(dir));
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

                if (shouldExclude(file, excludedFolders)) return FileVisitResult.CONTINUE;

                Path targetFile = destination.resolve(source.relativize(file));
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
        });

        return warnings.get();
    }

    private boolean isIncrementalNeeded(Path source, Path target, BasicFileAttributes sourceAttrs) throws IOException {
        if (!Files.exists(target)) return true;
        BasicFileAttributes targetAttrs = Files.readAttributes(target, BasicFileAttributes.class);
        return sourceAttrs.size() != targetAttrs.size() ||
                sourceAttrs.lastModifiedTime().toMillis() > targetAttrs.lastModifiedTime().toMillis();
    }

    private boolean shouldExclude(Path path, List<String> excludedFolders) {
        String p = path.toString();
        return excludedFolders.stream().anyMatch(p::contains);
    }
}
