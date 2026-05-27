package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class CollectionResponse<T> {

    private final boolean success;
    private final int count;
    private final List<T> items;
    private final String message;
    private final String requestId;
    private final LocalDateTime timestamp;

    public static <T> CollectionResponse<T> of(List<T> items) {
        return new CollectionResponse<>(true, items.size(), items, null, RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static <T> CollectionResponse<T> of(List<T> items, String message) {
        return new CollectionResponse<>(true, items.size(), items, message, RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }

    public static <T> CollectionResponse<T> empty(String message) {
        return new CollectionResponse<>(true, 0, List.of(), message, RequestTracingContext.currentRequestId(), LocalDateTime.now());
    }
}
