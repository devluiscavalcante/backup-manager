package com.backup_manager.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResponseTests {

    @Test
    void fromShouldExposePaginationMetadata() {
        Page<String> page = new PageImpl<>(
                List.of("x", "y"),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "startedAt")),
                5
        );

        PageResponse<String> response = PageResponse.from(page);

        assertTrue(response.isSuccess());
        assertEquals(List.of("x", "y"), response.getItems());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals("startedAt: DESC", response.getSort());
    }
}
