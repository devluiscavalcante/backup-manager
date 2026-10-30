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
    private LocalDateTime backupDate;
    private Long totalFiles;
    private BigDecimal totalSizeMB;
    private List<FileNodeDTO> files;
    // Backup anterior aos snapshots: o conteudo e o estado atual do destino, nao o desta execucao.
    private boolean legacyMirror;
    private String warning;
}