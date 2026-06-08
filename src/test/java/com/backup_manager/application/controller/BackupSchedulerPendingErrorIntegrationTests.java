package com.backup_manager.application.controller;

import com.backup_manager.application.service.BackupScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class BackupSchedulerPendingErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupScheduler backupScheduler;

    @Test
    void adminShouldReceiveStructuredErrorWhenPendingScheduledBackupsFail() throws Exception {
        when(backupScheduler.getPendingScheduledBackups()).thenThrow(new RuntimeException("scheduler_unavailable"));

        mockMvc.perform(get("/api/backup/scheduler/schedule/pending")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Erro interno ao listar backups pendentes."))
                .andExpect(jsonPath("$.code").value("scheduler_pending_list_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/schedule/pending"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("scheduler_unavailable"))));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
