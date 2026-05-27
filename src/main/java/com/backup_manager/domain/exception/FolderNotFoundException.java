package com.backup_manager.domain.exception;

public class FolderNotFoundException extends RuntimeException {

    private final String path;

    public FolderNotFoundException(String path) {
        super("Diretorio de origem nao encontrado.");
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
