package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Progress {

    private int percent;
    private String currentFile;
    private int processedFiles;
    private int totalFiles;
    private String taskId;
}
