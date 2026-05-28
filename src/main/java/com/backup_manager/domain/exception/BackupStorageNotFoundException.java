package com.backup_manager.domain.exception;

public class BackupStorageNotFoundException extends RuntimeException {

    private final Long backupId;
    private final String backupPath;

    public BackupStorageNotFoundException(Long backupId, String backupPath) {
        super("Os arquivos do backup nao estao disponiveis no disco.");
        this.backupId = backupId;
        this.backupPath = backupPath;
    }

    public Long getBackupId() {
        return backupId;
    }

    public String getBackupPath() {
        return backupPath;
    }
}
