package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileTreeDTO {

    private Long backupId;
    private String sourcePath;
    private String backupPath;
    // Pasta listada, relativa ao backup (vazia na raiz). So os filhos diretos vem em files.
    private String path;
    private LocalDateTime backupDate;
    private Long totalFiles;
    private BigDecimal totalSizeMB;
    private List<FileNodeDTO> files;
    // A pasta tem mais entradas do que o limite do preview: files traz so as primeiras.
    private boolean truncated;
    // Backup anterior aos snapshots: o conteudo e o estado atual do destino, nao o desta execucao.
    private boolean legacyMirror;
    private String warning;
}