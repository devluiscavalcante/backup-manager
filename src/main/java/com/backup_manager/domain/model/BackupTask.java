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
