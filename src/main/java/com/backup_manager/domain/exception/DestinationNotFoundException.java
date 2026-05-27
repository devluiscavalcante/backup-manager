package com.backup_manager.domain.exception;

public class DestinationNotFoundException extends RuntimeException {

    private final String path;

    public DestinationNotFoundException() {
        super("Pasta de destino nao encontrada.");
        this.path = null;
    }

    public DestinationNotFoundException(String destinationPath) {
        super("Pasta de destino nao encontrada.");
        this.path = destinationPath;
    }

    public String getPath() {
        return path;
    }
}
