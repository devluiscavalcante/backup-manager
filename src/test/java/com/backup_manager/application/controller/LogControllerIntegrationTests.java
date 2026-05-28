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
class LogControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminShouldInspectLogStatusWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Status dos logs carregado com sucesso"))
                .andExpect(jsonPath("$.data.logsAvailable").isBoolean())
                .andExpect(jsonPath("$.data.availableLogs").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectLogStatus() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
