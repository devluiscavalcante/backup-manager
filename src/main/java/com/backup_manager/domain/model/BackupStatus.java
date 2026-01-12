package com.backup_manager.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BackupStatus {

    private long fileCount;
    private double totalSizeMB;
}
