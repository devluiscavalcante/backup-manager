package com.backup_manager.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "scheduled_backups")
public class ScheduledBackupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_backup_sources",
            joinColumns = @JoinColumn(name = "scheduled_backup_id"))
    @Column(name = "source_path")
    private List<String> sources;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_backup_destinations",
            joinColumns = @JoinColumn(name = "scheduled_backup_id"))
    @Column(name = "destination_path")
    private List<String> destinations;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_execution")
    private LocalDateTime lastExecution;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}