package com.backup_manager.application.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HealthStatusResponseTests {

    @Test
    void shouldExposeSharedHealthFields() {
        LocalDateTime timestamp = LocalDateTime.now();
        HealthStatusResponse response = new HealthStatusResponse(
                "UP",
                "Backup Manager",
                "1.0.0",
                null,
                timestamp
        );

        assertEquals("UP", response.getStatus());
        assertEquals("Backup Manager", response.getService());
        assertEquals("1.0.0", response.getVersion());
        assertNull(response.getMessage());
        assertEquals(timestamp, response.getTimestamp());
    }
}
