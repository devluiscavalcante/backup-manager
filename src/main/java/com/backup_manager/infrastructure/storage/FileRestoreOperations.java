package com.backup_manager.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FileRestoreOperations {

    private static final Logger logger = LoggerFactory.getLogger(FileRestoreOperations.class);

    public interface RestoreProgressCallback {
        void onFileRestored(Path file, BasicFileAttributes attrs);
        void onWarning(String message, Path path);
        boolean shouldContinue();
        void onProgress(int processed, int total);
    }

    public RestoreResult restoreAll(Path backupSource, Path targetDestination,
                                    boolean overwriteExisting, RestoreProgressCallback callback) throws IOException {

        logger.info("Iniciando restauração completa: {} -> {}", backupSource, targetDestination);

        if (!Files.exists(backupSource)) {
            throw new IllegalArgumentException("Backup não encontrado: " + backupSource);
        }

        Files.createDirectories(targetDestination);

        AtomicInteger processedFiles = new AtomicInteger(0);
        AtomicInteger warnings = new AtomicInteger(0);
        AtomicLong totalSize = new AtomicLong(0);

        int totalFiles = countFiles(backupSource);

        Files.walkFileTree(backupSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!callback.shouldContinue()) {
                    return FileVisitResult.TERMINATE;
                }

                Path targetDir = targetDestination.resolve(backupSource.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!callback.shouldContinue()) {
                    return FileVisitResult.TERMINATE;
                }

                Path targetFile = targetDestination.resolve(backupSource.relativize(file));

                try {
                    if (!overwriteExisting && Files.exists(targetFile)) {
                        targetFile = generateUniqueFileName(targetFile);
                        callback.onWarning("Arquivo já existe, renomeado para", targetFile);
                        warnings.incrementAndGet();
                    }

                    Files.copy(file, targetFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);

                    totalSize.addAndGet(attrs.size());
                    int processed = processedFiles.incrementAndGet();

                    callback.onFileRestored(file, attrs);
                    callback.onProgress(processed, totalFiles);

                } catch (IOException e) {
                    callback.onWarning("Erro ao restaurar arquivo: " + e.getMessage(), file);
                    warnings.incrementAndGet();
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                callback.onWarning("Falha ao acessar arquivo: " + exc.getMessage(), file);
                warnings.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });

        logger.info("Restauração completa finalizada: {} arquivos, {} bytes",
                processedFiles.get(), totalSize.get());

        return new RestoreResult(processedFiles.get(), totalSize.get(), warnings.get());
    }

    public RestoreResult restoreSelective(Path backupSource, Path targetDestination,
                                          List<String> selectedPaths, boolean overwriteExisting,
                                          RestoreProgressCallback callback) throws IOException {

        logger.info("Iniciando restauração seletiva: {} arquivos selecionados", selectedPaths.size());

        if (!Files.exists(backupSource)) {
            throw new IllegalArgumentException("Backup não encontrado: " + backupSource);
        }

        Files.createDirectories(targetDestination);

        AtomicInteger processedFiles = new AtomicInteger(0);
        AtomicInteger warnings = new AtomicInteger(0);
        AtomicLong totalSize = new AtomicLong(0);

        int totalFilesToRestore = countSelectedFiles(backupSource, selectedPaths);

        for (String selectedPath : selectedPaths) {
            if (!callback.shouldContinue()) {
                break;
            }

            Path sourceFile = backupSource.resolve(selectedPath).normalize();

            if (!sourceFile.startsWith(backupSource)) {
                callback.onWarning("Path inválido (fora do backup)", sourceFile);
                warnings.incrementAndGet();
                continue;
            }

            if (!Files.exists(sourceFile)) {
                callback.onWarning("Arquivo não encontrado no backup", sourceFile);
                warnings.incrementAndGet();
                continue;
            }

            try {
                if (Files.isDirectory(sourceFile)) {
                    restoreDirectory(sourceFile, backupSource, targetDestination,
                            overwriteExisting, callback, processedFiles,
                            warnings, totalSize, totalFilesToRestore);
                } else {
                    restoreFile(sourceFile, backupSource, targetDestination,
                            overwriteExisting, callback, processedFiles,
                            warnings, totalSize, totalFilesToRestore);
                }
            } catch (IOException e) {
                callback.onWarning("Erro ao restaurar: " + e.getMessage(), sourceFile);
                warnings.incrementAndGet();
            }
        }

        logger.info("Restauração seletiva finalizada: {} arquivos, {} bytes",
                processedFiles.get(), totalSize.get());

        return new RestoreResult(processedFiles.get(), totalSize.get(), warnings.get());
    }

    private void restoreDirectory(Path sourceDir, Path backupRoot, Path targetRoot,
                                  boolean overwriteExisting, RestoreProgressCallback callback,
                                  AtomicInteger processedFiles, AtomicInteger warnings,
                                  AtomicLong totalSize, int totalFiles) throws IOException {

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!callback.shouldContinue()) {
                    return FileVisitResult.TERMINATE;
                }

                Path relativePath = backupRoot.relativize(dir);
                Path targetDir = targetRoot.resolve(relativePath);

                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!callback.shouldContinue()) {
                    return FileVisitResult.TERMINATE;
                }

                Path relativePath = backupRoot.relativize(file);
                Path targetFile = targetRoot.resolve(relativePath);

                try {
                    if (!overwriteExisting && Files.exists(targetFile)) {
                        targetFile = generateUniqueFileName(targetFile);
                        callback.onWarning("Arquivo já existe, renomeado para", targetFile);
                        warnings.incrementAndGet();
                    }

                    Files.copy(file, targetFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);

                    totalSize.addAndGet(attrs.size());
                    int processed = processedFiles.incrementAndGet();

                    callback.onFileRestored(file, attrs);
                    callback.onProgress(processed, totalFiles);

                } catch (IOException e) {
                    callback.onWarning("Erro ao restaurar arquivo: " + e.getMessage(), file);
                    warnings.incrementAndGet();
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void restoreFile(Path sourceFile, Path backupRoot, Path targetRoot,
                             boolean overwriteExisting, RestoreProgressCallback callback,
                             AtomicInteger processedFiles, AtomicInteger warnings,
                             AtomicLong totalSize, int totalFiles) throws IOException {

        Path relativePath = backupRoot.relativize(sourceFile);
        Path targetFile = targetRoot.resolve(relativePath);

        Files.createDirectories(targetFile.getParent());

        if (!overwriteExisting && Files.exists(targetFile)) {
            targetFile = generateUniqueFileName(targetFile);
            callback.onWarning("Arquivo já existe, renomeado para", targetFile);
            warnings.incrementAndGet();
        }

        Files.copy(sourceFile, targetFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        BasicFileAttributes attrs = Files.readAttributes(sourceFile, BasicFileAttributes.class);
        totalSize.addAndGet(attrs.size());
        int processed = processedFiles.incrementAndGet();

        callback.onFileRestored(sourceFile, attrs);
        callback.onProgress(processed, totalFiles);
    }

    private Path generateUniqueFileName(Path file) {
        String fileName = file.getFileName().toString();
        String baseName;
        String extension;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            extension = "";
        }

        Path parent = file.getParent();
        int counter = 1;
        Path newPath;

        do {
            String newFileName = baseName + "_restored_" + counter + extension;
            newPath = parent.resolve(newFileName);
            counter++;
        } while (Files.exists(newPath));

        return newPath;
    }

    private int countFiles(Path directory) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    private int countSelectedFiles(Path backupRoot, List<String> selectedPaths) throws IOException {
        AtomicInteger count = new AtomicInteger(0);

        for (String selectedPath : selectedPaths) {
            Path sourcePath = backupRoot.resolve(selectedPath).normalize();

            if (!sourcePath.startsWith(backupRoot) || !Files.exists(sourcePath)) {
                continue;
            }

            if (Files.isDirectory(sourcePath)) {
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        count.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                count.incrementAndGet();
            }
        }

        return count.get();
    }

    public static class RestoreResult {
        private final int filesRestored;
        private final long totalBytes;
        private final int warnings;

        public RestoreResult(int filesRestored, long totalBytes, int warnings) {
            this.filesRestored = filesRestored;
            this.totalBytes = totalBytes;
            this.warnings = warnings;
        }

        public int getFilesRestored() {
            return filesRestored;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public int getWarnings() {
            return warnings;
        }
    }
}