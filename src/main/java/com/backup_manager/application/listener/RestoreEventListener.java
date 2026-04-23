package com.backup_manager.application.listener;

import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.domain.event.RestoreCancelledEvent;
import com.backup_manager.domain.event.RestoreCompletedEvent;
import com.backup_manager.domain.event.RestoreFailedEvent;
import com.backup_manager.domain.event.RestoreStartedEvent;
import com.backup_manager.domain.model.RestoreTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RestoreEventListener {

    private static final Logger logger = LoggerFactory.getLogger(RestoreEventListener.class);

    private final EmailNotificationService emailService;

    public RestoreEventListener(EmailNotificationService emailService) {
        this.emailService = emailService;
    }

    @EventListener
    @Async
    public void handleRestoreStarted(RestoreStartedEvent event) {
        try {
            RestoreTask task = event.getTask();
            logger.info("Evento recebido: Restauração {} iniciada", task.getId());
            emailService.sendRestoreStartedNotification(task);
        } catch (Exception e) {
            logger.error("Erro ao processar evento RestoreStarted: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleRestoreCompleted(RestoreCompletedEvent event) {
        try {
            RestoreTask task = event.getTask();
            long duration = event.getDurationSeconds();
            logger.info("Evento recebido: Restauração {} concluída ({}s)", task.getId(), duration);
            emailService.sendRestoreCompletedNotification(task, duration);
        } catch (Exception e) {
            logger.error("Erro ao processar evento RestoreCompleted: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleRestoreFailed(RestoreFailedEvent event) {
        try {
            RestoreTask task = event.getTask();
            String error = event.getErrorMessage();
            logger.info("Evento recebido: Restauração {} falhou - {}", task.getId(), error);
            emailService.sendRestoreFailedNotification(task, error);
        } catch (Exception e) {
            logger.error("Erro ao processar evento RestoreFailed: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleRestoreCancelled(RestoreCancelledEvent event) {
        try {
            RestoreTask task = event.getTask();
            logger.info("Evento recebido: Restauração {} cancelada", task.getId());
            emailService.sendRestoreCancelledNotification(task);
        } catch (Exception e) {
            logger.error("Erro ao processar evento RestoreCancelled: {}", e.getMessage(), e);
        }
    }
}
