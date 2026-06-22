package com.backup_manager.domain.exception;

public class BackupInitializationException extends RuntimeException {

    public BackupInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
