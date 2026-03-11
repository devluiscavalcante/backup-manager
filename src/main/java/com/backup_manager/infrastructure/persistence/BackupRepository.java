package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BackupRepository extends JpaRepository<BackupTask, Long> {

    Optional<BackupTask> findTopByStatusOrderByFinishedAtDesc(Status status);
    Optional<BackupTask> findTopByOrderByFinishedAtDesc();
    List<BackupTask> findBySourcePathAndDestinationPathOrderByIdDesc(String sourcePath, String destinationPath);

    @Query("SELECT b FROM BackupTask b WHERE b.status IN :statuses")
    List<BackupTask> findByStatusIn(@Param("statuses") List<Status> statuses);

    List<BackupTask> findByStatus(Status status);

    Page<BackupTask> findByStatus(Status status, Pageable pageable);

    Page<BackupTask> findByStartedAtBetween(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    Page<BackupTask> findByStatusAndStartedAtBetween(
            Status status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT b FROM BackupTask b ORDER BY b.startedAt DESC")
    Page<BackupTask> findAllOrderByStartedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(b) FROM BackupTask b WHERE b.status = :status")
    Long countByStatus(@Param("status") Status status);

    @Query("SELECT COALESCE(SUM(b.totalSizeMB), 0) FROM BackupTask b WHERE b.status = :status")
    BigDecimal sumSizeByStatus(@Param("status") Status status);

    @Query("SELECT b FROM BackupTask b ORDER BY b.startedAt DESC")
    List<BackupTask> findTopNByOrderByStartedAtDesc(Pageable pageable);
}