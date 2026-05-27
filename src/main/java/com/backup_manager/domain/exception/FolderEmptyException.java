package com.backup_manager.domain.exception;

public class FolderEmptyException extends RuntimeException {

    private final String path;

    public FolderEmptyException(String path) {
        super("A pasta de origem esta vazia.");
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
