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
        "app.security.operator-password=operator-secret",
        "app.version=1.5.0-SNAPSHOT"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminShouldInspectDatabaseHealthWithSchemaSummary() throws Exception {
        mockMvc.perform(get("/api/health/database")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("PostgreSQL"))
                .andExpect(jsonPath("$.details.healthy").value(true))
                .andExpect(jsonPath("$.details.currentVersion").value("5"))
                .andExpect(jsonPath("$.details.appliedMigrations").value(5))
                .andExpect(jsonPath("$.details.orphanedHistory").value(false))
                .andExpect(jsonPath("$.details.missingManagedTables").isArray())
                .andExpect(jsonPath("$.details.missingManagedTables.length()").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectDatabaseHealth() throws Exception {
        mockMvc.perform(get("/api/health/database")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/health/database"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void applicationHealthShouldExposeMinimalPublicRuntimeDetails() throws Exception {
        mockMvc.perform(get("/api/health/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Backup Manager"))
                .andExpect(jsonPath("$.version").value("1.5.0-SNAPSHOT"))
                .andExpect(jsonPath("$.details.publicEndpoint").value(true))
                .andExpect(jsonPath("$.details.defaultTimeZone").isString())
                .andExpect(jsonPath("$.details.requestTracingHeader").value("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
