package com.backup_manager.application.controller;

import com.backup_manager.domain.model.ScheduledBackupEntity;
import com.backup_manager.infrastructure.persistence.ScheduledBackupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class BackupConfigControllerDeleteIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduledBackupRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void deleteShouldReturnStructuredSuccessResponse() throws Exception {
        ScheduledBackupEntity entity = new ScheduledBackupEntity();
        entity.setName("Daily backup");
        entity.setSources(List.of("C:/Users/luis/Documents"));
        entity.setDestinations(List.of("C:/Users/luis/Backup"));
        entity.setCronExpression("0 0 2 * * *");
        entity.setEnabled(true);

        ScheduledBackupEntity saved = repository.save(entity);

        mockMvc.perform(delete("/api/backup/config/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("Configuracao de backup removida com sucesso"));
    }

    @Test
    void deleteShouldReturnStructuredNotFoundResponse() throws Exception {
        mockMvc.perform(delete("/api/backup/config/999999")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("scheduler_config_not_found"))
                .andExpect(jsonPath("$.path").value("/api/backup/config/999999"));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
