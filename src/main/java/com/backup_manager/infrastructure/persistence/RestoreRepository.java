package com.backup_manager.infrastructure.persistence;

import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RestoreRepository extends JpaRepository<RestoreTask, Long> {

    List<RestoreTask> findByStatus(RestoreStatus status);

    Page<RestoreTask> findByStatus(RestoreStatus status, Pageable pageable);

    @Query("SELECT r FROM RestoreTask r ORDER BY r.startedAt DESC")
    Page<RestoreTask> findAllOrderByStartedAtDesc(Pageable pageable);

    @Query("SELECT r FROM RestoreTask r WHERE r.sourceBackup.id = :backupId ORDER BY r.startedAt DESC")
    List<RestoreTask> findByBackupId(@Param("backupId") Long backupId);

    @Query("SELECT r FROM RestoreTask r WHERE r.startedAt BETWEEN :startDate AND :endDate ORDER BY r.startedAt DESC")
    Page<RestoreTask> findByStartedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT COUNT(r) FROM RestoreTask r WHERE r.status = :status")
    Long countByStatus(@Param("status") RestoreStatus status);

    @Query("SELECT r FROM RestoreTask r ORDER BY r.startedAt DESC")
    List<RestoreTask> findTopNByOrderByStartedAtDesc(Pageable pageable);
}