package com.backup_manager.application.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionResponseTests {

    @Test
    void ofShouldExposeCountAndItems() {
        CollectionResponse<String> response = CollectionResponse.of(List.of("a", "b"));

        assertTrue(response.isSuccess());
        assertEquals(2, response.getCount());
        assertEquals(List.of("a", "b"), response.getItems());
    }

    @Test
    void emptyShouldReturnEmptyCollectionWithMessage() {
        CollectionResponse<String> response = CollectionResponse.empty("No items");

        assertTrue(response.isSuccess());
        assertEquals(0, response.getCount());
        assertEquals(List.of(), response.getItems());
        assertEquals("No items", response.getMessage());
    }
}
