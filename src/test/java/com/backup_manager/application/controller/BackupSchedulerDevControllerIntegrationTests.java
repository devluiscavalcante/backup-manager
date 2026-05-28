package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.service.BackupScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.allow-default-password=true",
        "app.security.password=admin-secret",
        "app.security.operator-enabled=true",
        "app.security.operator-password=operator-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class BackupSchedulerDevControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupScheduler backupScheduler;

    @Test
    void adminShouldScheduleDevelopmentBackupWithStandardEnvelope() throws Exception {
        when(backupScheduler.scheduleOneTimeBackup(ArgumentMatchers.any(BackupRequest.class), anyInt(), anyString()))
                .thenReturn(77L);

        mockMvc.perform(post("/api/backup/scheduler/test-5min")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Backup de desenvolvimento agendado com sucesso"))
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                .andExpect(jsonPath("$.taskId").value(77))
                .andExpect(jsonPath("$.backupName").value("Backup Dev"))
                .andExpect(jsonPath("$.cancelUrl").value("/api/backup/scheduler/schedule/77/cancel"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStructuredErrorForDevelopmentBackupFailure() throws Exception {
        when(backupScheduler.scheduleOneTimeBackup(ArgumentMatchers.any(BackupRequest.class), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("scheduler_unavailable"));

        mockMvc.perform(post("/api/backup/scheduler/test-quick")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Erro no endpoint de teste do scheduler."))
                .andExpect(jsonPath("$.code").value("scheduler_dev_test_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/test-quick"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
