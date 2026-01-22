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

        return emitter;
    }

    public void sendProgress(Progress progress) {
        String payload = createProgressPayload(progress);
        sendEvent("progress", payload);
    }

    public void sendComplete(String message) {
        String payload = String.format("{\"message\":\"%s\"}", escapeJson(message));
        sendEvent("complete", payload);
    }

    public void sendError(String error) {
        String payload = String.format("{\"error\":\"%s\"}", escapeJson(error));
        sendEvent("error", payload);
    }

    // Método para enviar eventos de controle
    public void sendControlEvent(String eventType, Long taskId, String status) {
        String payload = String.format(
                "{\"type\":\"%s\",\"taskId\":%d,\"status\":\"%s\",\"timestamp\":%d}",
                escapeJson(eventType), taskId, escapeJson(status), System.currentTimeMillis()
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

    private String createProgressPayload(Progress progress) {
        return String.format(
                "{\"percent\":%d,\"currentFile\":\"%s\",\"processedFiles\":%d,\"totalFiles\":%d,\"taskId\":\"%s\"}",
                progress.getPercent(),
                escapeJson(progress.getCurrentFile()),
                progress.getProcessedFiles(),
                progress.getTotalFiles(),
                escapeJson(progress.getTaskId())
        );
    }

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