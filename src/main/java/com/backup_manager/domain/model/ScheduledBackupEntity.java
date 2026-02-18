package com.backup_manager.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "scheduled_backups")
public class ScheduledBackupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> sources;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> destinations;

    private String cronExpression;

    private boolean enabled = true;
}
