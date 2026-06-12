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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
