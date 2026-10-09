package com.backup_manager.application.progress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.allow-default-password=true",
        "app.security.password=admin-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressStreamIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void progressStreamShouldSendConnectedEventImmediately() throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString("admin:admin-secret".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(get("/api/backup/progress")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("event:connected")
                .contains("data:{}");
    }
}
