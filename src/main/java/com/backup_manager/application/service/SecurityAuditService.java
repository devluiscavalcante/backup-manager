package com.backup_manager.application.service;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.StringJoiner;

@Service
public class SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void recordSuccess(String action, String resource, Map<String, ?> details) {
        logger.info(buildMessage("SUCCESS", action, resource, null, details));
    }

    public void recordFailure(String action, String resource, String reason, Map<String, ?> details) {
        logger.warn(buildMessage("FAILURE", action, resource, reason, details));
    }

    String buildMessage(String outcome,
                        String action,
                        String resource,
                        String reason,
                        Map<String, ?> details) {
        AuditActor actor = resolveActor();
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("outcome=" + outcome);
        joiner.add("action=" + sanitize(action));
        joiner.add("actor=" + sanitize(actor.username()));
        joiner.add("roles=" + sanitize(actor.roles()));
        joiner.add("resource=" + sanitize(resource));
        String requestId = RequestTracingContext.currentRequestId();
        if (requestId != null) {
            joiner.add("requestId=" + sanitize(requestId));
        }

        if (reason != null && !reason.isBlank()) {
            joiner.add("reason=\"" + escape(reason) + "\"");
        }

        if (details != null && !details.isEmpty()) {
            details.forEach((key, value) -> {
                if (value != null) {
                    joiner.add(sanitize(key) + "=\"" + escape(String.valueOf(value)) + "\"");
                }
            });
        }

        return joiner.toString();
    }

    private AuditActor resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new AuditActor("anonymous", "NONE");
        }

        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("NONE");

        return new AuditActor(authentication.getName(), roles);
    }

    private String sanitize(String value) {
        return value == null ? "unknown" : value.replace(' ', '_');
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record AuditActor(String username, String roles) {
    }
}
