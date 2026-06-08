package com.backup_manager.application.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class BackupSchedulerControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminShouldInspectSchedulerStatusWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/backup/scheduler/status")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Status do scheduler carregado com sucesso"))
                .andExpect(jsonPath("$.data.enabled").isBoolean())
                .andExpect(jsonPath("$.data.cronExpression").isString())
                .andExpect(jsonPath("$.data.timeZone").isString())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldInspectSchedulerInfoWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/backup/scheduler/info")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Resumo do scheduler carregado com sucesso"))
                .andExpect(jsonPath("$.data.status").isString())
                .andExpect(jsonPath("$.data.enabled").isBoolean())
                .andExpect(jsonPath("$.data.cronExpression").isString())
                .andExpect(jsonPath("$.data.timeZone").isString())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldInspectSchedulerHealthWithDetails() throws Exception {
        mockMvc.perform(get("/api/backup/scheduler/health")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("backup-scheduler"))
                .andExpect(jsonPath("$.details.schedulerEnabled").isBoolean())
                .andExpect(jsonPath("$.details.cronExpression").isString())
                .andExpect(jsonPath("$.details.timeZone").isString())
                .andExpect(jsonPath("$.details.totalConfigurations").isNumber())
                .andExpect(jsonPath("$.details.enabledConfigurations").isNumber())
                .andExpect(jsonPath("$.details.recentExecutions").isNumber())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectSchedulerHealth() throws Exception {
        mockMvc.perform(get("/api/backup/scheduler/health")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/health"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void operatorShouldNotInspectSchedulerStatus() throws Exception {
        mockMvc.perform(get("/api/backup/scheduler/status")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/status"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStructuredErrorForInvalidScheduleDelay() throws Exception {
        mockMvc.perform(post("/api/backup/scheduler/schedule-once")
                        .queryParam("minutesFromNow", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source"],
                                  "destination": ["C:/temp/destination"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Minutos devem ser maior que 0."))
                .andExpect(jsonPath("$.code").value("scheduler_minutes_out_of_range"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/schedule-once"))
                .andExpect(jsonPath("$.details.minutesFromNow").value(0))
                .andExpect(jsonPath("$.details.min").value(1))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStructuredErrorWhenScheduleDelayIsMissing() throws Exception {
        mockMvc.perform(post("/api/backup/scheduler/schedule-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": ["C:/temp/source"],
                                  "destination": ["C:/temp/destination"]
                                }
                                """)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Parametro de requisicao obrigatorio ausente."))
                .andExpect(jsonPath("$.code").value("request_parameter_missing"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/schedule-once"))
                .andExpect(jsonPath("$.details.parameter").value("minutesFromNow"))
                .andExpect(jsonPath("$.details.expectedType").value("int"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldReceiveStructuredErrorWhenScheduledTaskDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/backup/scheduler/schedule/999999/cancel")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Tarefa nao encontrada ou ja executada/cancelada."))
                .andExpect(jsonPath("$.code").value("scheduler_task_not_found_or_completed"))
                .andExpect(jsonPath("$.path").value("/api/backup/scheduler/schedule/999999/cancel"))
                .andExpect(jsonPath("$.taskId").value(999999))
                .andExpect(jsonPath("$.details.taskId").value(999999))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
