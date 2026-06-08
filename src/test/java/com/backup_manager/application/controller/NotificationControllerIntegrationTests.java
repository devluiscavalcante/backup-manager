package com.backup_manager.application.controller;

import com.backup_manager.application.service.EmailNotificationService;
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

import static org.mockito.Mockito.when;
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
class NotificationControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailNotificationService emailService;

    @Test
    void adminShouldInspectNotificationSettingsWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/backup/notifications/settings")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Configuracoes de notificacao carregadas com sucesso"))
                .andExpect(jsonPath("$.data.notificationsEnabled").isBoolean())
                .andExpect(jsonPath("$.data.emailEnabled").isBoolean())
                .andExpect(jsonPath("$.data.fromAddress").isString())
                .andExpect(jsonPath("$.data.recipientsCount").isNumber())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectNotificationSettings() throws Exception {
        mockMvc.perform(get("/api/backup/notifications/settings")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/backup/notifications/settings"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStructuredErrorWhenTestEmailFails() throws Exception {
        when(emailService.sendTestEmail()).thenReturn(false);

        mockMvc.perform(post("/api/backup/notifications/test")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Falha ao enviar email de teste."))
                .andExpect(jsonPath("$.code").value("notification_test_email_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/notifications/test"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
