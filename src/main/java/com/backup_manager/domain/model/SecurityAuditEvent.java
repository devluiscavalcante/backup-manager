package com.backup_manager.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "security_audit_events", indexes = {
        @Index(name = "idx_security_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_security_audit_outcome_created", columnList = "outcome, created_at"),
        @Index(name = "idx_security_audit_actor_created", columnList = "actor, created_at"),
        @Index(name = "idx_security_audit_request_id", columnList = "request_id")
})
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "action", nullable = false, length = 150)
    private String action;

    @Column(name = "actor", nullable = false, length = 150)
    private String actor;

    @Column(name = "roles", nullable = false, length = 300)
    private String roles;

    @Column(name = "resource", nullable = false, length = 150)
    private String resource;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
