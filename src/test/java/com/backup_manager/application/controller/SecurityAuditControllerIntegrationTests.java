package com.backup_manager.application.controller;

import com.backup_manager.domain.model.AuditOutcome;
import com.backup_manager.domain.model.SecurityAuditEvent;
import com.backup_manager.infrastructure.persistence.SecurityAuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.allow-default-password=true",
        "app.security.password=admin-secret",
        "app.security.operator-enabled=true",
        "app.security.operator-password=operator-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuditControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityAuditEventRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void adminShouldQueryPersistedAuditEvents() throws Exception {
        repository.save(createEvent("backup.start", "admin", "trace-123", AuditOutcome.SUCCESS));
        repository.save(createEvent("restore.cancel", "operator", "trace-999", AuditOutcome.FAILURE));

        mockMvc.perform(get("/api/system/audit")
                        .param("requestId", "trace-123")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("backup.start"))
                .andExpect(jsonPath("$.items[0].actor").value("admin"))
                .andExpect(jsonPath("$.items[0].requestId").value("trace-123"))
                .andExpect(jsonPath("$.items[0].details.taskId").value(15));
    }

    @Test
    void operatorShouldNotQueryAuditEvents() throws Exception {
        mockMvc.perform(get("/api/system/audit")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden());
    }

    private SecurityAuditEvent createEvent(String action, String actor, String requestId, AuditOutcome outcome) {
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setAction(action);
        event.setActor(actor);
        event.setRoles("ROLE_ADMIN");
        event.setOutcome(outcome);
        event.setResource("backup_request");
        event.setRequestId(requestId);
        event.setDetailsJson("{\"taskId\":15}");
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
