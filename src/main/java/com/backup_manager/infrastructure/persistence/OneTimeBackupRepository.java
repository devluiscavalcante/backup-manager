package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.OneTimeBackupEntity;
import com.backup_manager.domain.model.OneTimeBackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OneTimeBackupRepository extends JpaRepository<OneTimeBackupEntity, Long> {

    List<OneTimeBackupEntity> findByStatusOrderByScheduledForAsc(OneTimeBackupStatus status);
}
