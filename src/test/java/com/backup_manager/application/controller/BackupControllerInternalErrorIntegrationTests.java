package com.backup_manager.application.controller;

import com.backup_manager.application.service.BackupHistoryService;
import com.backup_manager.application.service.BackupRequestValidationService;
import com.backup_manager.application.service.BackupService;
import com.backup_manager.application.service.SecurityAuditService;
import com.backup_manager.domain.exception.BackupInitializationException;
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
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class BackupControllerInternalErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupHistoryService historyService;

    @MockitoBean
    private BackupService backupService;

    @MockitoBean
    private BackupRequestValidationService backupRequestValidationService;

    @MockitoBean
    private SecurityAuditService securityAuditService;

    @Test
    void backupStartShouldReturnStructuredErrorWhenInitializationFails() throws Exception {
        when(backupService.getActiveTask("C:\\source", "D:\\destination"))
                .thenReturn(Optional.empty());
        when(backupService.runBackup("C:\\source", "D:\\destination"))
                .thenThrow(new BackupInitializationException(
                        "Falha ao preparar o backup.",
                        new RuntimeException("disk_unavailable")
                ));

        mockMvc.perform(post("/api/backup/start")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "sources": ["C:\\\\source"],
                                  "destination": ["D:\\\\destination"]
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Erro interno ao preparar backup."))
                .andExpect(jsonPath("$.code").value("backup_start_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/start"))
                .andExpect(jsonPath("$.details.sourceCount").value(1))
                .andExpect(jsonPath("$.details.destinationCount").value(1))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("disk_unavailable"))));

        verify(securityAuditService).recordFailure(
                "backup.start",
                "backup_request",
                "initialization_failed",
                Map.of("sourceCount", 1, "destinationCount", 1)
        );
    }

    @Test
    void backupStatisticsShouldReturnStructuredErrorForUnexpectedFailure() throws Exception {
        when(historyService.getStatistics()).thenThrow(new RuntimeException("database_unavailable"));

        mockMvc.perform(get("/api/backup/history/stats")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Erro interno ao calcular estatisticas."))
                .andExpect(jsonPath("$.code").value("backup_statistics_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/history/stats"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("database_unavailable"))));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
