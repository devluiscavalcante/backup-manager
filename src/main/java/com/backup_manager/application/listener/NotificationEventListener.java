package com.backup_manager.application.listener;

import com.backup_manager.domain.event.*;
import com.backup_manager.application.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventListener.class);

    private final EmailNotificationService emailService;

    public NotificationEventListener(EmailNotificationService emailService) {
        this.emailService = emailService;
    }

    @EventListener
    @Async
    public void handleBackupStarted(BackupStartedEvent event) {
        logger.info("Evento recebido: Backup {} iniciado ({})",
                event.getTask().getId(), event.isScheduled() ? "agendado" : "manual");

        try {
            emailService.sendStartedNotification(event.getTask(), event.isScheduled());
        } catch (Exception e) {
            logger.error("Erro ao processar notificação de início: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleBackupScheduled(BackupScheduledEvent event) {
        logger.info("Evento recebido: Backup '{}' agendado para {}",
                event.getBackupName(), event.getNextExecution());

        try {
            emailService.sendScheduledNotification(
                    event.getBackupName(),
                    event.getSources(),
                    event.getDestinations(),
                    event.getNextExecution(),
                    event.getCronExpression()
            );
        } catch (Exception e) {
            logger.error("Erro ao processar notificação de agendamento: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleBackupCompleted(BackupCompletedEvent event) {
        logger.info("Evento recebido: Backup {} concluído", event.getTask().getId());

        try {
            emailService.sendSuccessNotification(event.getTask(), event.getDurationSeconds());
        } catch (Exception e) {
            logger.error("Erro ao processar notificação de sucesso: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleBackupFailed(BackupFailedEvent event) {
        logger.info("Evento recebido: Backup {} falhou", event.getTask().getId());

        try {
            emailService.sendFailureNotification(event.getTask(), event.getErrorMessage());
        } catch (Exception e) {
            logger.error("Erro ao processar notificação de falha: {}", e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleBackupCancelled(BackupCancelledEvent event) {
        logger.info("Evento recebido: Backup {} cancelado", event.getTask().getId());

        try {
            emailService.sendCancellationNotification(event.getTask());
        } catch (Exception e) {
            logger.error("Erro ao processar notificação de cancelamento: {}", e.getMessage(), e);
        }
    }
}