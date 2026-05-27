package com.backup_manager.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.backup_manager.domain.model.SecurityAuditEvent;
import com.backup_manager.infrastructure.persistence.SecurityAuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditServiceTests {

    private final SecurityAuditEventRepository repository = mock(SecurityAuditEventRepository.class);
    private final SecurityAuditService securityAuditService = new SecurityAuditService(repository, new ObjectMapper());
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        when(repository.save(any(SecurityAuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        auditLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void recordSuccessShouldLogAuthenticatedActorWithStructuredDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "operator.user",
                        "secret",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_OPERATOR"),
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                )
        );

        securityAuditService.recordSuccess(
                "backup.start",
                "backup_request",
                Map.of("taskCount", 2, "taskIds", List.of(11L, 12L))
        );

        assertThat(listAppender.list).hasSize(1);

        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("outcome=SUCCESS");
        assertThat(event.getFormattedMessage()).contains("action=backup.start");
        assertThat(event.getFormattedMessage()).contains("actor=operator.user");
        assertThat(event.getFormattedMessage()).contains("roles=ROLE_ADMIN,ROLE_OPERATOR");
        assertThat(event.getFormattedMessage()).contains("resource=backup_request");
        assertThat(event.getFormattedMessage()).contains("taskCount=\"2\"");
        assertThat(event.getFormattedMessage()).contains("taskIds=\"[11, 12]\"");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("operator.user");
        assertThat(captor.getValue().getDetailsJson()).contains("\"taskCount\":2");
    }

    @Test
    void recordFailureShouldFallbackToAnonymousWhenAuthenticationIsMissing() {
        securityAuditService.recordFailure(
                "scheduler.cancel",
                "scheduled_backup_task",
                "task_not_found",
                Map.of("taskId", 99L)
        );

        assertThat(listAppender.list).hasSize(1);

        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("outcome=FAILURE");
        assertThat(event.getFormattedMessage()).contains("action=scheduler.cancel");
        assertThat(event.getFormattedMessage()).contains("actor=anonymous");
        assertThat(event.getFormattedMessage()).contains("roles=NONE");
        assertThat(event.getFormattedMessage()).contains("resource=scheduled_backup_task");
        assertThat(event.getFormattedMessage()).contains("reason=\"task_not_found\"");
        assertThat(event.getFormattedMessage()).contains("taskId=\"99\"");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("anonymous");
        assertThat(captor.getValue().getReason()).isEqualTo("task_not_found");
    }
}
