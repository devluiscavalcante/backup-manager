package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.storage.BackupManifest;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Resultado da verificacao de um snapshot contra o manifesto gravado na execucao. */
@Getter
@AllArgsConstructor
public class BackupVerificationResponse {

    // Um backup corrompido pode divergir em milhares de arquivos: a resposta traz a contagem e uma amostra.
    static final int MAX_LISTED = 100;

    private final Long backupId;
    private final String snapshotPath;
    private final boolean intact;
    private final int checkedFiles;
    private final int missingCount;
    private final int changedCount;
    private final int unexpectedCount;
    private final List<String> missing;
    private final List<String> changed;
    private final List<String> unexpected;

    public static BackupVerificationResponse of(Long backupId, String snapshotPath,
                                                BackupManifest.Verification verification) {
        return new BackupVerificationResponse(
                backupId,
                snapshotPath,
                verification.intact(),
                verification.checked(),
                verification.missing().size(),
                verification.changed().size(),
                verification.unexpected().size(),
                sample(verification.missing()),
                sample(verification.changed()),
                sample(verification.unexpected())
        );
    }

    private static List<String> sample(List<String> paths) {
        return paths.size() <= MAX_LISTED ? paths : paths.subList(0, MAX_LISTED);
    }
}
