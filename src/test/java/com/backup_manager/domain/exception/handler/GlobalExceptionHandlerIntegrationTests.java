package com.backup_manager.domain.exception.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
class GlobalExceptionHandlerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnStructuredValidationErrorBody() throws Exception {
        mockMvc.perform(post("/api/backup/start")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("request_validation_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/start"))
                .andExpect(jsonPath("$.details.sources").value("A lista de origens nao pode estar vazia"))
                .andExpect(jsonPath("$.details.destination").value("A lista de destinos nao pode estar vazia"));
    }

    @Test
    void shouldReturnStructuredNotFoundBodyForSchedulerConfig() throws Exception {
        mockMvc.perform(get("/api/backup/config/999999")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("scheduler_config_not_found"))
                .andExpect(jsonPath("$.path").value("/api/backup/config/999999"));
    }

    @Test
    void shouldReturnStructuredBackupNotFoundBodyForRestorePreview() throws Exception {
        mockMvc.perform(get("/api/backup/999999/restore/preview")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Backup nao encontrado."))
                .andExpect(jsonPath("$.code").value("backup_not_found"))
                .andExpect(jsonPath("$.path").value("/api/backup/999999/restore/preview"))
                .andExpect(jsonPath("$.details.backupId").value(999999));
    }

    @Test
    void shouldReturnStructuredFolderEmptyBodyForBackupStart() throws Exception {
        Path emptySource = Files.createDirectory(tempDir.resolve("empty-source"));
        Path destination = Files.createDirectory(tempDir.resolve("backup-destination"));

        String payload = """
                {
                  "sources": ["%s"],
                  "destination": ["%s"]
                }
                """.formatted(escapePath(emptySource), escapePath(destination));

        mockMvc.perform(post("/api/backup/start")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("A pasta de origem esta vazia."))
                .andExpect(jsonPath("$.code").value("source_folder_empty"))
                .andExpect(jsonPath("$.path").value("/api/backup/start"))
                .andExpect(jsonPath("$.details.source").value(emptySource.toString()));
    }

    @Test
    void shouldReturnStructuredValidationBodyForMismatchedSourceAndDestinationCounts() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("file.txt"), "content");
        Path destination = Files.createDirectory(tempDir.resolve("destination"));

        String payload = """
                {
                  "sources": ["%s", "%s"],
                  "destination": ["%s"]
                }
                """.formatted(escapePath(source), escapePath(source), escapePath(destination));

        mockMvc.perform(post("/api/backup/start")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("O numero de origens deve ser igual ao numero de destinos."))
                .andExpect(jsonPath("$.code").value("operation_validation_failed"))
                .andExpect(jsonPath("$.path").value("/api/backup/start"));
    }

    @Test
    void shouldReturnStructuredForbiddenBodyForPathTraversalAttempt() throws Exception {
        String payload = """
                {
                  "sources": ["..\\\\segredo"],
                  "destination": ["..\\\\destino"]
                }
                """;

        mockMvc.perform(post("/api/backup/start")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Path traversal detectado na operacao de backup."))
                .andExpect(jsonPath("$.code").value("operation_not_allowed"))
                .andExpect(jsonPath("$.path").value("/api/backup/start"));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String escapePath(Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
