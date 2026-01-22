package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BackupRepository extends JpaRepository<BackupTask, Long> {

    Optional<BackupTask> findTopByStatusOrderByFinishedAtDesc(Status status);
    Optional<BackupTask> findTopByOrderByFinishedAtDesc();

    List<BackupTask> findBySourcePathAndDestinationPathOrderByIdDesc(String sourcePath, String destinationPath);

    @Query("SELECT b FROM BackupTask b WHERE b.status IN :statuses")
    List<BackupTask> findByStatusIn(@Param("statuses") List<Status> statuses);

    List<BackupTask> findByStatus(Status status);
}