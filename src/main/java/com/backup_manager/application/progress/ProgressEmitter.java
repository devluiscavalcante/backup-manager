package com.backup_manager.application.progress;

import com.backup_manager.application.dto.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgressEmitter {

    private static final Logger logger = LoggerFactory.getLogger(ProgressEmitter.class);
    private static final long DEFAULT_TIMEOUT = 1000L * 60 * 30; // 30 minutos
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.add(emitter);

        emitter.onTimeout(() -> {
            logger.debug("Emitter timeout");
            emitters.remove(emitter);
        });

        emitter.onCompletion(() -> {
            logger.debug("Emitter completed");
            emitters.remove(emitter);
        });

        emitter.onError((e) -> {
            logger.error("Emitter error: {}", e.getMessage());
            emitters.remove(emitter);
        });

        return emitter;
    }

    public void sendProgress(Progress progress) {
        String payload = createProgressPayload(progress);
        sendEvent("progress", payload);
    }

    public void sendComplete(String message) {
        String payload = String.format("{\"message\":\"%s\"}", message);
        sendEvent("complete", payload);
    }

    public void sendError(String error) {
        String payload = String.format("{\"error\":\"%s\"}", error);
        sendEvent("error", payload);
    }

    // Método para enviar eventos de controle
    public void sendControlEvent(String eventType, Long taskId, String status) {
        String payload = String.format(
                "{\"type\":\"%s\",\"taskId\":%d,\"status\":\"%s\",\"timestamp\":%d}",
                eventType, taskId, status, System.currentTimeMillis()
        );
        sendEvent("control", payload);
    }

    // Método genérico para enviar eventos
    private void sendEvent(String eventName, String data) {
        if (emitters.isEmpty()) {
            return;
        }

        Set<SseEmitter> deadEmitters = ConcurrentHashMap.newKeySet();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                logger.debug("Evento '{}' enviado: {}", eventName, data.substring(0, Math.min(data.length(), 100)));
            } catch (IOException | IllegalStateException e) {
                logger.warn("Erro ao enviar evento para emitter: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        // Remover emitters mortos
        emitters.removeAll(deadEmitters);
    }

    // Cria payload JSON manualmente (sem ObjectMapper)
    private String createProgressPayload(Progress progress) {
        return String.format(
                "{\"percent\":%d,\"currentFile\":\"%s\",\"processedFiles\":%d,\"totalFiles\":%d,\"taskId\":\"%s\"}",
                progress.getPercent(),
                escapeJson(progress.getCurrentFile()),
                progress.getProcessedFiles(),
                progress.getTotalFiles(),
                progress.getTaskId()
        );
    }

    // Escapar caracteres especiais para JSON
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}