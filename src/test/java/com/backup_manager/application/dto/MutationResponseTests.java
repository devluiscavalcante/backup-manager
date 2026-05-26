package com.backup_manager.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationResponseTests {

    @Test
    void successShouldExposePayloadAndMessage() {
        MutationResponse<String> response = MutationResponse.success("payload", "Saved successfully");

        assertTrue(response.isSuccess());
        assertEquals("payload", response.getData());
        assertEquals("Saved successfully", response.getMessage());
        assertNull(response.getDetails());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void successWithDetailsShouldPreserveOptionalDetails() {
        MutationResponse<Integer> response = MutationResponse.success(42, "Updated", "Next run in 5 minutes");

        assertTrue(response.isSuccess());
        assertEquals(42, response.getData());
        assertEquals("Updated", response.getMessage());
        assertEquals("Next run in 5 minutes", response.getDetails());
    }
}
