package com.backup_manager.application.progress;

import com.backup_manager.application.dto.Progress;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressEmitterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProgressEmitter emitter = new ProgressEmitter(objectMapper);

    @Test
    void progressPayloadShouldStayValidJsonForAnyFileName() throws Exception {
        // Caracteres de controle (ex.: \u0001) quebravam o JSON montado com String.format.
        String fileName = "relat\"orio\\\u0001\u001f\tfinal.txt";

        String json = emitter.toProgressJson(new Progress(42, fileName, 10, 20, "7"));

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("currentFile").asText()).isEqualTo(fileName);
        assertThat(node.get("percent").asInt()).isEqualTo(42);
        assertThat(node.get("processedFiles").asInt()).isEqualTo(10);
        assertThat(node.get("totalFiles").asInt()).isEqualTo(20);
        assertThat(node.get("taskId").asText()).isEqualTo("7");
    }
}
