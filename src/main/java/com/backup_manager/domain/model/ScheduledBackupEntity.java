package com.backup_manager.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "O nome do agendamento nao pode estar vazio")
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_backup_sources",
            joinColumns = @JoinColumn(name = "scheduled_backup_id"))
    @Column(name = "source_path")
    @NotEmpty(message = "A lista de origens nao pode estar vazia")
    @Size(max = 20, message = "A lista de origens nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Origem nao pode estar em branco") String> sources;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_backup_destinations",
            joinColumns = @JoinColumn(name = "scheduled_backup_id"))
    @Column(name = "destination_path")
    @NotEmpty(message = "A lista de destinos nao pode estar vazia")
    @Size(max = 20, message = "A lista de destinos nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Destino nao pode estar em branco") String> destinations;

    @Column(name = "cron_expression", nullable = false)
    @NotBlank(message = "A expressao cron nao pode estar vazia")
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
