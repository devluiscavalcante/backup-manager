package com.backup_manager.application.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationResponseTests {

    @Test
    void backupStartedShouldExposeTaskIdsAndOperationalStatus() {
        OperationResponse response = OperationResponse.backupStarted(
                "Backup started",
                List.of(10L, 11L)
        );

        assertTrue(response.isSuccess());
        assertEquals("EM_ANDAMENTO", response.getStatus());
        assertEquals(List.of(10L, 11L), response.getTaskIds());
        assertNull(response.getTaskId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void scheduledShouldExposeSchedulingMetadata() {
        LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(5);
        OperationResponse response = OperationResponse.scheduled(
                "Scheduled",
                99L,
                "Backup Agendado",
                scheduledTime,
                "/api/backup/scheduler/schedule/99/cancel"
        );

        assertTrue(response.isSuccess());
        assertEquals("AGENDADO", response.getStatus());
        assertEquals(99L, response.getTaskId());
        assertEquals("Backup Agendado", response.getBackupName());
        assertEquals(scheduledTime, response.getScheduledTime());
        assertEquals("/api/backup/scheduler/schedule/99/cancel", response.getCancelUrl());
    }

    @Test
    void selectiveRestoreStartedShouldExposeFilesCount() {
        OperationResponse response = OperationResponse.selectiveRestoreStarted(
                55L,
                7,
                "Restore started"
        );

        assertTrue(response.isSuccess());
        assertEquals("EM_ANDAMENTO", response.getStatus());
        assertEquals(55L, response.getTaskId());
        assertEquals(7, response.getFilesCount());
        assertNull(response.getTaskIds());
    }
}
