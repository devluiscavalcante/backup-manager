package com.backup_manager.application.dto;

import com.backup_manager.domain.model.SecurityAuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SecurityAuditEventResponse {

    private final Long id;
    private final String outcome;
    private final String action;
    private final String actor;
    private final String roles;
    private final String resource;
    private final String reason;
    private final String requestId;
    private final Object details;
    private final LocalDateTime createdAt;

    public static SecurityAuditEventResponse fromEntity(SecurityAuditEvent event, ObjectMapper objectMapper) {
        return new SecurityAuditEventResponse(
                event.getId(),
                event.getOutcome().name(),
                event.getAction(),
                event.getActor(),
                event.getRoles(),
                event.getResource(),
                event.getReason(),
                event.getRequestId(),
                parseDetails(event.getDetailsJson(), objectMapper),
                event.getCreatedAt()
        );
    }

    private static Object parseDetails(String detailsJson, ObjectMapper objectMapper) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(detailsJson, Object.class);
        } catch (JsonProcessingException ignored) {
            return detailsJson;
        }
    }
}
