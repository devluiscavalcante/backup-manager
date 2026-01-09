package com.backup_manager.domain.service;

import com.backup_manager.domain.exception.DestinationNotFoundException;
import com.backup_manager.domain.exception.FolderEmptyException;
import com.backup_manager.domain.exception.FolderNotFoundException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
        long totalBytes = calculateFolderSize(folder);
        double sizeInMB = totalBytes / (1024.0 * 1024.0);
        return BigDecimal.valueOf(sizeInMB).setScale(2, RoundingMode.HALF_UP);
    }

    public long countFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return 0;

        long count = 0;
        for (File file : files) {
            if (file.isFile()) {
                count++;
            } else if (file.isDirectory()) {
                count += countFiles(file);
            }
        }
        return count;
    }

    private long calculateFolderSize(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return 0;

        long total = 0;
        for (File file : files) {
            if (file.isFile()) {
                total += file.length();
            } else if (file.isDirectory()) {
                total += calculateFolderSize(file);
            }
        }
        return total;
    }
}
