package com.backup_manager.application.progress;

import com.backup_manager.application.dto.Progress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgressEmitter {

    private static final Logger logger = LoggerFactory.getLogger(ProgressEmitter.class);
    private static final long DEFAULT_TIMEOUT = 1000L * 60 * 30; // 30 minutos
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public ProgressEmitter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.add(emitter);

        emitter.onTimeout(() -> {
            logger.debug("SSE Emitter timeout - client disconnected");
            emitters.remove(emitter);
        });

        emitter.onCompletion(() -> {
            logger.debug("SSE Emitter completed normally");
            emitters.remove(emitter);
        });

        emitter.onError((e) -> {

            if (isNormalDisconnect(e)) {
                logger.debug("SSE client disconnected normally: {}", e.getMessage());
            } else {
                logger.error("SSE Emitter error: {}", e.getMessage(), e);
            }
            emitters.remove(emitter);
        });

        // Sem um primeiro evento o servlet nao envia os cabecalhos, e clientes baseados em fetch
        // ficam aguardando a resposta ate o proximo progresso de backup.
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException e) {
            logger.debug("SSE client disconnected before handshake: {}", e.getMessage());
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void sendProgress(Progress progress) {
        sendEvent("progress", toProgressJson(progress));
    }

    String toProgressJson(Progress progress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("percent", progress.getPercent());
        payload.put("currentFile", nullToEmpty(progress.getCurrentFile()));
        payload.put("processedFiles", progress.getProcessedFiles());
        payload.put("totalFiles", progress.getTotalFiles());
        payload.put("taskId", nullToEmpty(progress.getTaskId()));
        return toJson(payload);
    }

    public void sendComplete(String message) {
        sendEvent("complete", toJson(Map.of("message", nullToEmpty(message))));
    }

    public void sendError(String error) {
        sendEvent("error", toJson(Map.of("error", nullToEmpty(error))));
    }

    // Método para enviar eventos de controle
    public void sendControlEvent(String eventType, Long taskId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", nullToEmpty(eventType));
        payload.put("taskId", taskId);
        payload.put("status", nullToEmpty(status));
        payload.put("timestamp", System.currentTimeMillis());
        sendEvent("control", toJson(payload));
    }

    // Nomes de arquivo podem ter qualquer caractere (inclusive de controle): o escape fica com o Jackson.
    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.warn("Falha ao serializar evento SSE: {}", e.getMessage());
            return "{}";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

                if (logger.isDebugEnabled()) {
                    logger.debug("SSE event '{}' sent to client: {}...",
                            eventName, data.length() > 50 ? data.substring(0, 50) + "..." : data);
                }
            } catch (IOException e) {

                if (isNormalDisconnect(e)) {
                    logger.debug("SSE client disconnected during event send: {}", e.getMessage());
                } else {
                    logger.warn("SSE IOException: {}", e.getMessage());
                }
                deadEmitters.add(emitter);
            } catch (IllegalStateException e) {

                logger.debug("SSE emitter in illegal state (probably completed): {}", e.getMessage());
                deadEmitters.add(emitter);
            } catch (Exception e) {

                logger.warn("Unexpected error sending SSE event: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            logger.debug("Removed {} disconnected SSE emitters", deadEmitters.size());
        }
    }

    private boolean isNormalDisconnect(Throwable e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        String className = e.getClass().getName();

        if (message != null) {
            message = message.toLowerCase();

            if (message.contains("connection reset") ||
                    message.contains("broken pipe") ||
                    message.contains("connection closed") ||
                    message.contains("an established connection was aborted") ||
                    message.contains("uma conexão estabelecida foi anulada") ||
                    message.contains("servlet container error notification for disconnected client") ||
                    message.contains("async request not usable")) {
                return true;
            }
        }

        if (className.contains("ClientAbortException") ||
                className.contains("EofException") ||
                className.contains("AsyncRequestNotUsableException")) {
            return true;
        }

        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isNormalDisconnect(cause);
        }

        return false;
    }
}
