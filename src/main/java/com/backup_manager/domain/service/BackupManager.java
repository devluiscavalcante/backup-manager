package com.backup_manager.domain.service;

import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Classe responsável pelas regras de negócio relacionadas ao processo de backup.
 * Não acessa banco de dados nem executa comandos do sistema.
 * Apenas aplica validações e cálculos necessários.
 */
@Component
public class BackupManager {

    public File validateSource(String sourcePath) {
        File sourceFolder = new File(sourcePath);

        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            throw new FolderNotFoundException(sourcePath);
        }

        String[] files = sourceFolder.list();
        if (files == null || files.length == 0) {
            throw new FolderEmptyException(sourcePath);
        }

        return sourceFolder;
    }

    public void validateDestination(String destinationPath) {
        File destinationFolder = new File(destinationPath);
        if (!destinationFolder.exists() || !destinationFolder.isDirectory()) {
            throw new DestinationNotFoundException(destinationPath);
        }
    }

    public BigDecimal calculateFolderSizeMB(File folder) {
        long totalBytes = scanRegularFiles(folder).totalBytes();
        double sizeInMB = totalBytes / (1024.0 * 1024.0);
        return BigDecimal.valueOf(sizeInMB).setScale(2, RoundingMode.HALF_UP);
    }

    public long countFiles(File folder) {
        return scanRegularFiles(folder).fileCount();
    }

    // Percorre a arvore sem seguir symlinks/junctions (evita contar conteudo externo e loops por links ciclicos).
    private FolderScan scanRegularFiles(File folder) {
        Path root;
        try {
            root = folder.toPath().toRealPath();
        } catch (IOException e) {
            return new FolderScan(0, 0);
        }

        long[] totals = {0, 0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return dir.equals(root) || !isRedirected(dir)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        totals[0]++;
                        totals[1] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return new FolderScan(totals[0], totals[1]);
        }

        return new FolderScan(totals[0], totals[1]);
    }

    private static boolean isRedirected(Path dir) {
        try {
            return !dir.toRealPath().equals(dir);
        } catch (IOException e) {
            return true;
        }
    }

    private record FolderScan(long fileCount, long totalBytes) {
    }
}
