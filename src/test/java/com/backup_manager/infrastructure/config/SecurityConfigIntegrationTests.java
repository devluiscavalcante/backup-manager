package com.backup_manager.infrastructure.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SecurityConfigIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationHealthShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health/application"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedOperationalRequestShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/backup/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"backup-manager\""))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Autenticacao obrigatoria."))
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.path").value("/api/backup/active"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unauthenticatedXhrRequestShouldNotReceiveBasicChallenge() throws Exception {
        mockMvc.perform(get("/api/backup/active")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void operatorShouldAccessOperationalEndpoints() throws Exception {
        mockMvc.perform(get("/api/backup/active")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isOk());
    }

    @Test
    void operatorShouldNotAccessAdministrativeEndpoints() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acesso negado para este recurso."))
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.path").value("/api/logs"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminShouldAccessAdministrativeEndpoints() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedWrongPasswordsShouldLockTheClientEvenForCorrectCredentials() throws Exception {
        // IP proprio para nao bloquear os demais testes que compartilham o contexto.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/backup/active")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.7");
                                return request;
                            })
                            .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "palpite-" + i)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/backup/active")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("too_many_failed_logins"));

        mockMvc.perform(get("/api/backup/active")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isOk());
    }

    @Test
    void crossOriginRejectionShouldNotResetFailedLoginCounter() throws Exception {
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(get("/api/backup/active")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.8");
                                return request;
                            })
                            .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "palpite-" + i)))
                    .andExpect(status().isUnauthorized());
        }

        // 403 do filtro de origem cruzada acontece antes da autenticacao e nao pode zerar o contador.
        mockMvc.perform(post("/api/backup/start")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.8");
                            return request;
                        })
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "palpite-x")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/backup/active")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.8");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "palpite-final")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/backup/active")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.8");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("admin", "admin-secret")))
                .andExpect(status().isTooManyRequests());
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
