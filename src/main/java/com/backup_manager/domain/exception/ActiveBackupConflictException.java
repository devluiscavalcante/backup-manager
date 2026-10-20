package com.backup_manager.domain.exception;

/**
 * Ja existe backup ativo para o mesmo par origem/destino (comparado pelos caminhos reais).
 * Estende IllegalStateException para seguir tratada como erro de pre-condicao onde nao houver handler proprio.
 */
public class ActiveBackupConflictException extends IllegalStateException {

    private final Long conflictingTaskId;
    private final String sourcePath;
    private final String destinationPath;

    public ActiveBackupConflictException(Long conflictingTaskId, String sourcePath, String destinationPath) {
        super("Ja existe um backup ativo para este par origem/destino");
        this.conflictingTaskId = conflictingTaskId;
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
    }

    public Long getConflictingTaskId() {
        return conflictingTaskId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getDestinationPath() {
        return destinationPath;
    }
}
