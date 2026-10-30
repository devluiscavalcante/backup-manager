package com.backup_manager.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "backup_tasks", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_source_dest", columnList = "source_path, destination_path"),
        @Index(name = "idx_started_at", columnList = "started_at"),
        @Index(name = "idx_status_started", columnList = "status, started_at")
})
public class BackupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_path", nullable = false, length = 1000)
    private String sourcePath;

    @Column(name = "destination_path", nullable = false, length = 1000)
    private String destinationPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "file_count")
    private Long fileCount;

    @Column(name = "total_size_mb", precision = 10, scale = 2)
    private BigDecimal totalSizeMB;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "is_paused", nullable = false)
    private boolean paused = false;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    // Pasta do snapshot desta execucao. Nulo em backups anteriores aos snapshots: o conteudo deles e o
    // espelho da raiz do destino, ou seja, o estado da ultima execucao e nao o desta.
    @Column(name = "snapshot_path", length = 1000)
    private String snapshotPath;

    @Column(name = "snapshot_removed_at")
    private LocalDateTime snapshotRemovedAt;

    // Pausa, cancelamento e finalizacao vem de threads diferentes: sem versao, o ultimo save vencia
    // (ex.: o worker gravando CONCLUIDO por cima de um CANCELADO feito instantes antes).
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Pasta com o conteudo desta execucao: o snapshot ou, em backups antigos, o espelho da raiz do destino. */
    public String contentPath() {
        return snapshotPath != null ? snapshotPath : destinationPath;
    }

    /** Backup anterior aos snapshots: restaurar devolve o estado atual do destino, nao o desta execucao. */
    public boolean legacyMirror() {
        return snapshotPath == null;
    }

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = Status.EM_ANDAMENTO;
        }
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
