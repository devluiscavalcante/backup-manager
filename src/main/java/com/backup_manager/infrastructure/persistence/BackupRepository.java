package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BackupRepository extends JpaRepository<BackupTask, Long> {

    Optional<BackupTask> findTopByStatusOrderByFinishedAtDesc(Status status);
    Optional<BackupTask> findTopByOrderByFinishedAtDesc();
}
