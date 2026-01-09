package com.backup_manager.application.progress;

import com.backup_manager.application.dto.Progress;
import io.jsonwebtoken.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.jdbc.support.DatabaseStartupValidator.DEFAULT_TIMEOUT;

public class ProgressEmitter {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter((long) DEFAULT_TIMEOUT);
        emitters.add(emitter);

        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    public void sendProgress(Progress dto) {
        String payload;
        try {
            payload = mapper.writeValueAsString(dto);
        } catch (Exception e) {
            payload = "{\"percent\":0,\"currentFile\":\"serialization_error\",\"processed\":0,\"total\":0}";
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(payload));
            } catch (IOException | java.io.IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void sendComplete(String message) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("complete").data(message));
                emitter.complete();
            } catch (IOException | java.io.IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
