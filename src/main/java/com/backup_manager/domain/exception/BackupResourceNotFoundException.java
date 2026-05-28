package com.backup_manager.domain.exception;

public class BackupResourceNotFoundException extends RuntimeException {

    private final Long backupId;

    public BackupResourceNotFoundException(Long backupId) {
        super("Backup nao encontrado.");
        this.backupId = backupId;
    }

    public Long getBackupId() {
        return backupId;
    }
}
