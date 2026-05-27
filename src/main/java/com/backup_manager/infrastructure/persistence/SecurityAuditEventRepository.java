package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long>,
        JpaSpecificationExecutor<SecurityAuditEvent> {
}
