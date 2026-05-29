package com.backup_manager.application.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@ActiveProfiles("test")
class BackupControllerErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void historySearchShouldReturnStructuredErrorForInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/backup/history/search")
                        .queryParam("startDate", "2026-05-02T00:00:00")
                        .queryParam("endDate", "2026-05-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Data inicial nao pode ser posterior a data final."))
                .andExpect(jsonPath("$.code").value("invalid_date_range"))
                .andExpect(jsonPath("$.path").value("/api/backup/history/search"))
                .andExpect(jsonPath("$.details.startDate").value("2026-05-02T00:00:00"))
                .andExpect(jsonPath("$.details.endDate").value("2026-05-01T00:00:00"));
    }

    @Test
    void recentBackupsShouldReturnStructuredErrorForInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/backup/history/recent")
                        .queryParam("limit", "101")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("limit_out_of_range"))
                .andExpect(jsonPath("$.path").value("/api/backup/history/recent"))
                .andExpect(jsonPath("$.details.limit").value(101))
                .andExpect(jsonPath("$.details.min").value(1))
                .andExpect(jsonPath("$.details.max").value(100));
    }

    @Test
    void backupStatusShouldReturnStructuredErrorForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/backup/999999/status")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Tarefa de backup nao encontrada."))
                .andExpect(jsonPath("$.code").value("backup_task_not_found"))
                .andExpect(jsonPath("$.path").value("/api/backup/999999/status"))
                .andExpect(jsonPath("$.taskId").value(999999))
                .andExpect(jsonPath("$.details.taskId").value(999999));
    }

    @Test
    void pauseBackupShouldReturnStructuredErrorForUnknownTask() throws Exception {
        mockMvc.perform(post("/api/backup/999999/pause")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("backup_task_not_found_or_invalid"))
                .andExpect(jsonPath("$.path").value("/api/backup/999999/pause"))
                .andExpect(jsonPath("$.taskId").value(999999))
                .andExpect(jsonPath("$.details.action").value("pause"));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
