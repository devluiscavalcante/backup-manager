package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduledBackupRepository extends JpaRepository<ScheduledBackupEntity, Long>{

    List<ScheduledBackupEntity> findByEnabledTrue();
}
