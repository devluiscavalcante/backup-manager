package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PageResponse<T> {

    private final boolean success;
    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;
    private final String sort;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                true,
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getSort().toString(),
                RequestTracingContext.currentRequestId(),
                LocalDateTime.now()
        );
    }
}
