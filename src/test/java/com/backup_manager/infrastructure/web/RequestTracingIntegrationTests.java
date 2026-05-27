package com.backup_manager.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class RequestTracingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdHeaderWhenClientDoesNotSendOne() throws Exception {
        mockMvc.perform(get("/api/health/application"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestTracingContext.HEADER_NAME, not(blankOrNullString())));
    }

    @Test
    void shouldPropagateClientRequestIdToHeaderAndResponseBody() throws Exception {
        String requestId = "trace-req-123";

        mockMvc.perform(get("/api/backup/active")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("operator", "operator-secret"))
                        .header(RequestTracingContext.HEADER_NAME, requestId))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestTracingContext.HEADER_NAME, requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
