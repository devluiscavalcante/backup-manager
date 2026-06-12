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
class RestoreControllerErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void restoreHistoryShouldReturnStructuredErrorForInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/restore/history")
                        .queryParam("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Tamanho da pagina deve estar entre 1 e 100."))
                .andExpect(jsonPath("$.code").value("page_size_out_of_range"))
                .andExpect(jsonPath("$.path").value("/api/restore/history"))
                .andExpect(jsonPath("$.details.size").value(0))
                .andExpect(jsonPath("$.details.min").value(1))
                .andExpect(jsonPath("$.details.max").value(100));
    }

    @Test
    void recentRestoresShouldReturnStructuredErrorForInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/restore/recent")
                        .queryParam("limit", "101")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("limit_out_of_range"))
                .andExpect(jsonPath("$.path").value("/api/restore/recent"))
                .andExpect(jsonPath("$.details.limit").value(101))
                .andExpect(jsonPath("$.details.min").value(1))
                .andExpect(jsonPath("$.details.max").value(100));
    }

    @Test
    void restoreHistoryShouldReturnStructuredErrorForNegativePage() throws Exception {
        mockMvc.perform(get("/api/restore/history")
                        .queryParam("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Indice da pagina nao pode ser negativo."))
                .andExpect(jsonPath("$.code").value("page_index_out_of_range"))
                .andExpect(jsonPath("$.path").value("/api/restore/history"))
                .andExpect(jsonPath("$.details.page").value(-1))
                .andExpect(jsonPath("$.details.min").value(0));
    }

    @Test
    void restoreHistoryShouldReturnStructuredErrorForInvalidSortField() throws Exception {
        mockMvc.perform(get("/api/restore/history")
                        .queryParam("sortBy", "unknownField")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Campo de ordenacao invalido."))
                .andExpect(jsonPath("$.code").value("invalid_sort_field"))
                .andExpect(jsonPath("$.path").value("/api/restore/history"))
                .andExpect(jsonPath("$.details.sortBy").value("unknownField"))
                .andExpect(jsonPath("$.details.allowedFields").isArray())
                .andExpect(jsonPath("$.details.allowedFields[0]").value("id"))
                .andExpect(jsonPath("$.details.allowedFields[1]").value("status"))
                .andExpect(jsonPath("$.details.allowedFields[2]").value("startedAt"))
                .andExpect(jsonPath("$.details.allowedFields[7]").value("restoredFiles"));
    }

    @Test
    void restoreStatusShouldReturnStructuredErrorForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/restore/999999/status")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Tarefa de restauracao nao encontrada."))
                .andExpect(jsonPath("$.code").value("restore_task_not_found_or_invalid"))
                .andExpect(jsonPath("$.path").value("/api/restore/999999/status"))
                .andExpect(jsonPath("$.taskId").value(999999))
                .andExpect(jsonPath("$.details.action").value("status"));
    }

    @Test
    void cancelRestoreShouldReturnStructuredErrorForUnknownTask() throws Exception {
        mockMvc.perform(post("/api/restore/999999/cancel")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("restore_task_not_found_or_invalid"))
                .andExpect(jsonPath("$.path").value("/api/restore/999999/cancel"))
                .andExpect(jsonPath("$.taskId").value(999999))
                .andExpect(jsonPath("$.details.action").value("cancel"));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
