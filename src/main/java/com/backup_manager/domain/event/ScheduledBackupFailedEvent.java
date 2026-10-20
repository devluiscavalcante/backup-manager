package com.backup_manager.domain.event;

import lombok.Getter;

import java.util.List;

/**
 * Execucao agendada que nao conseguiu iniciar um ou mais pares origem/destino
 * (ex.: disco desmontado, falta de espaco). Sem tarefa criada nao ha BackupFailedEvent.
 */
@Getter
public class ScheduledBackupFailedEvent {
    private final String backupName;
    private final List<String> failures;

    public ScheduledBackupFailedEvent(String backupName, List<String> failures) {
        this.backupName = backupName;
        this.failures = List.copyOf(failures);
    }
}
