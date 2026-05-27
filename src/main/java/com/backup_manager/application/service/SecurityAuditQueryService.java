package com.backup_manager.application.service;

import com.backup_manager.application.dto.SecurityAuditEventResponse;
import com.backup_manager.domain.model.AuditOutcome;
import com.backup_manager.domain.model.SecurityAuditEvent;
import com.backup_manager.infrastructure.persistence.SecurityAuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SecurityAuditQueryService {

    private final SecurityAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public SecurityAuditQueryService(SecurityAuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Page<SecurityAuditEventResponse> search(String action,
                                                   AuditOutcome outcome,
                                                   String actor,
                                                   String requestId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate,
                                                   Pageable pageable) {
        return repository.findAll(buildSpecification(action, outcome, actor, requestId, startDate, endDate), pageable)
                .map(event -> SecurityAuditEventResponse.fromEntity(event, objectMapper));
    }

    private Specification<SecurityAuditEvent> buildSpecification(String action,
                                                                 AuditOutcome outcome,
                                                                 String actor,
                                                                 String requestId,
                                                                 LocalDateTime startDate,
                                                                 LocalDateTime endDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null && !action.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("action")),
                        "%" + action.toLowerCase() + "%"
                ));
            }

            if (outcome != null) {
                predicates.add(criteriaBuilder.equal(root.get("outcome"), outcome));
            }

            if (actor != null && !actor.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("actor")),
                        "%" + actor.toLowerCase() + "%"
                ));
            }

            if (requestId != null && !requestId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("requestId"), requestId));
            }

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
