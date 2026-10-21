package com.backup_manager.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "one_time_backups")
public class OneTimeBackupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "one_time_backup_sources",
            joinColumns = @JoinColumn(name = "one_time_backup_id"))
    @Column(name = "source_path")
    private List<String> sources;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "one_time_backup_destinations",
            joinColumns = @JoinColumn(name = "one_time_backup_id"))
    @Column(name = "destination_path")
    private List<String> destinations;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OneTimeBackupStatus status = OneTimeBackupStatus.PENDENTE;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
