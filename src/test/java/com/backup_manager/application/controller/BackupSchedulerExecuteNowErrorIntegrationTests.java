package com.backup_manager.application.controller;

import com.backup_manager.application.dto.BackupRequest;
import com.backup_manager.application.service.BackupScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class BackupSchedulerExecuteNowErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupScheduler backupScheduler;

    @Test
    void adminShouldReceiveConflictWhenNoBackupStarts() throws Exception {
        when(backupScheduler.executeBackupWithRequest(
                ArgumentMatchers.any(BackupRequest.class),
                anyString()
        )).thenReturn(List.of());

        mockMvc.perform(post("/api/backup/scheduler/execute-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source"],
                                  "destination": ["C:/temp/destination"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("active_backup_conflict"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/execute-now"))
                .andExpect(jsonPath("$.details.requestedCount").value(1))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStartedTaskIds() throws Exception {
        when(backupScheduler.executeBackupWithRequest(
                ArgumentMatchers.any(BackupRequest.class),
                anyString()
        )).thenReturn(List.of(41L, 42L));

        mockMvc.perform(post("/api/backup/scheduler/execute-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source-a", "C:/temp/source-b"],
                                  "destination": ["C:/temp/destination-a", "C:/temp/destination-b"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.taskIds[0]").value(41))
                .andExpect(jsonPath("$.taskIds[1]").value(42))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveInternalServerErrorWhenExecuteNowUnexpectedlyFails() throws Exception {
        doThrow(new RuntimeException("scheduler_unavailable"))
                .when(backupScheduler)
                .executeBackupWithRequest(ArgumentMatchers.any(BackupRequest.class), anyString());

        mockMvc.perform(post("/api/backup/scheduler/execute-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source"],
                                  "destination": ["C:/temp/destination"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Erro interno ao executar backup imediato."))
                .andExpect(jsonPath("$.code").value("scheduler_execute_now_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/execute-now"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("scheduler_unavailable"))));
    }

    @Test
    void adminShouldReceiveBadRequestWhenExecuteNowValidationFails() throws Exception {
        doThrow(new IllegalArgumentException("invalid_backup_request"))
                .when(backupScheduler)
                .executeBackupWithRequest(ArgumentMatchers.any(BackupRequest.class), anyString());

        mockMvc.perform(post("/api/backup/scheduler/execute-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source"],
                                  "destination": ["C:/temp/destination"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Nao foi possivel executar backup imediato com os dados informados."))
                .andExpect(jsonPath("$.code").value("scheduler_execute_now_validation_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/execute-now"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("invalid_backup_request"))));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
