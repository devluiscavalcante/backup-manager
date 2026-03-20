package com.backup_manager.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "restore_tasks")
@Data
public class RestoreTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backup_task_id", nullable = false)
    private BackupTask sourceBackup;

    @Column(name = "target_path", nullable = false, length = 500)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "restore_type", nullable = false, length = 20)
    private RestoreType restoreType;

    @Column(name = "selected_files", columnDefinition = "TEXT")
    private String selectedFiles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RestoreStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "file_count")
    private Long fileCount;

    @Column(name = "total_size_mb", precision = 10, scale = 2)
    private BigDecimal totalSizeMB;

    @Column(name = "restored_files")
    private Long restoredFiles = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "is_cancelled")
    private boolean cancelled = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}