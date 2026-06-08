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
class SystemControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminShouldInspectSchemaStatus() throws Exception {
        mockMvc.perform(get("/api/system/schema")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.schema").value("public"))
                .andExpect(jsonPath("$.historyTable").value("flyway_schema_history"))
                .andExpect(jsonPath("$.currentVersion").value("5"))
                .andExpect(jsonPath("$.appliedMigrations").value(5))
                .andExpect(jsonPath("$.existingTables").isArray())
                .andExpect(jsonPath("$.existingTables[?(@ == 'backup_tasks')]").exists())
                .andExpect(jsonPath("$.missingManagedTables").isArray())
                .andExpect(jsonPath("$.missingManagedTables.length()").value(0))
                .andExpect(jsonPath("$.orphanedHistory").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectSchemaStatus() throws Exception {
        mockMvc.perform(get("/api/system/schema")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/system/schema"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
