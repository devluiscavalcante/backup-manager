package com.backup_manager.application.controller;

import com.backup_manager.infrastructure.persistence.RestoreRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.when;
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
class RestoreControllerInternalErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestoreRepository restoreRepository;

    @Test
    void restoreHistoryShouldReturnStructuredErrorForUnexpectedFailure() throws Exception {
        when(restoreRepository.findAllOrderByStartedAtDesc(ArgumentMatchers.any(Pageable.class)))
                .thenThrow(new RuntimeException("database_unavailable"));

        mockMvc.perform(get("/api/restore/history")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Erro interno ao buscar historico de restauracoes."))
                .andExpect(jsonPath("$.code").value("restore_history_list_failed"))
                .andExpect(jsonPath("$.path").value("/api/restore/history"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
