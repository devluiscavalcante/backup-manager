package com.backup_manager.application.service;

import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.infrastructure.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 2000;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;
    private final RetryTemplate criticalEmailRetry;

    @Autowired
    public EmailNotificationService(JavaMailSender mailSender,
                                    NotificationProperties notificationProperties) {
        this(mailSender, notificationProperties, INITIAL_BACKOFF_MS);
    }

    EmailNotificationService(JavaMailSender mailSender,
                             NotificationProperties notificationProperties,
                             long initialBackoffMs) {
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
        this.criticalEmailRetry = buildCriticalEmailRetry(initialBackoffMs);
    }

    /**
     * Retry programatico: @Retryable exigiria @EnableRetry e uma chamada via proxy,
     * o que nao acontece em metodos privados chamados pela propria classe.
     * Credenciais recusadas nao sao transitorias, entao nao vale repetir.
     */
    private static RetryTemplate buildCriticalEmailRetry(long initialBackoffMs) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                MAX_DELIVERY_ATTEMPTS,
                Map.of(
                        MailAuthenticationException.class, false,
                        MailException.class, true
                ),
                false
        );

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialBackoffMs);
        backOffPolicy.setMultiplier(BACKOFF_MULTIPLIER);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Async
    public void sendStartedNotification(BackupTask task, boolean isScheduled) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnStarted()) {
            logger.debug("Notificacao de inicio desabilitada");
            return;
        }

        String subject = isScheduled ? "Backup Agendado Iniciado" : "Backup Iniciado";
        String body = buildStartedEmail(task, isScheduled);
        sendEmail(subject, body);
    }

    @Async
    public void sendScheduledNotification(String backupName, List<String> sources,
                                          List<String> destinations, LocalDateTime nextExecution,
                                          String cronExpression) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnScheduled()) {
            logger.debug("Notificacao de agendamento desabilitada");
            return;
        }

        String subject = "Novo Backup Agendado";
        String body = buildScheduledEmail(backupName, sources, destinations, nextExecution, cronExpression);
        sendEmail(subject, body);
    }

    @Async
    public void sendSuccessNotification(BackupTask task, long durationSeconds) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnSuccess()) {
            logger.debug("Notificacao de sucesso desabilitada");
            return;
        }

        String subject = "Backup Concluido com Sucesso";
        String body = buildSuccessEmail(task, durationSeconds);
        sendEmail(subject, body);
    }

    @Async
    public void sendFailureNotification(BackupTask task, String errorMessage) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnFailure()) {
            logger.debug("Notificacao de falha desabilitada");
            return;
        }

        sendEmailWithRetry("Falha no Backup", buildFailureEmail(task, errorMessage));
    }

    @Async
    public void sendCancellationNotification(BackupTask task) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnCancellation()) {
            logger.debug("Notificacao de cancelamento desabilitada");
            return;
        }

        String subject = "Backup Cancelado";
        String body = buildCancellationEmail(task);
        sendEmail(subject, body);
    }

    @Async
    public void sendRestoreStartedNotification(RestoreTask task) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnStarted()) {
            logger.debug("Notificacao de inicio de restauracao desabilitada");
            return;
        }

        sendEmail("Restauracao Iniciada", buildRestoreStartedEmail(task));
    }

    @Async
    public void sendRestoreCompletedNotification(RestoreTask task, long durationSeconds) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnSuccess()) {
            logger.debug("Notificacao de sucesso de restauracao desabilitada");
            return;
        }

        sendEmail("Restauracao Concluida com Sucesso",
                buildRestoreCompletedEmail(task, durationSeconds));
    }

    @Async
    public void sendRestoreFailedNotification(RestoreTask task, String errorMessage) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnFailure()) {
            logger.debug("Notificacao de falha de restauracao desabilitada");
            return;
        }

        sendEmailWithRetry("Falha na Restauracao", buildRestoreFailureEmail(task, errorMessage));
    }

    @Async
    public void sendRestoreCancelledNotification(RestoreTask task) {
        if (!shouldSendEmail() || !notificationProperties.getEmail().isNotifyOnCancellation()) {
            logger.debug("Notificacao de cancelamento de restauracao desabilitada");
            return;
        }

        sendEmail("Restauracao Cancelada", buildRestoreCancellationEmail(task));
    }

    public boolean sendTestEmail() {
        if (!shouldSendEmail()) {
            logger.warn("Email desabilitado. Configure notification.email.enabled=true");
            return false;
        }

        String subject = "Email de Teste - Sistema de Backups";
        String body = buildTestEmail();

        try {
            sendEmail(subject, body);
            return true;
        } catch (Exception e) {
            logger.error("Erro ao enviar email de teste: {}", e.getMessage());
            return false;
        }
    }

    /** Envio simples: MailException propaga (o envio de teste depende disso para reportar falha). */
    private void sendEmail(String subject, String body) {
        try {
            deliver(subject, body);
        } catch (MessagingException e) {
            logger.error("Erro ao enviar email '{}': {}", subject, e.getMessage());
        }
    }

    /** Envio de notificacoes criticas (falhas): repete erros transitorios e nunca propaga excecao. */
    private void sendEmailWithRetry(String subject, String body) {
        criticalEmailRetry.execute(
                context -> {
                    if (context.getRetryCount() > 0) {
                        logger.warn("Reenviando email critico '{}' (tentativa {}/{})",
                                subject, context.getRetryCount() + 1, MAX_DELIVERY_ATTEMPTS);
                    }
                    try {
                        deliver(subject, body);
                    } catch (MessagingException e) {
                        throw new MailPreparationException(e);
                    }
                    return null;
                },
                context -> {
                    logger.error("Falha definitiva ao enviar email critico '{}' apos {} tentativa(s): {}",
                            subject, context.getRetryCount(), context.getLastThrowable().getMessage());
                    return null;
                }
        );
    }

    private void deliver(String subject, String body) throws MessagingException {
        List<String> recipients = notificationProperties.getEmail().getRecipients();

        if (recipients == null || recipients.isEmpty()) {
            logger.warn("Nenhum destinatario configurado em notification.email.recipients");
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(notificationProperties.getEmail().getFrom());
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(body, true);

        mailSender.send(message);
        logger.info("Email enviado: '{}'", subject);
    }

    private boolean shouldSendEmail() {
        return notificationProperties.isEnabled() && notificationProperties.getEmail().isEnabled();
    }

    private String buildStartedEmail(BackupTask task, boolean isScheduled) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";
        String type = isScheduled ? "agendado" : "manual";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #2196F3; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Backup Iniciado</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Tipo:</strong> %s</p>
                        <p><strong>Origem:</strong> %s</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Iniciado em:</strong> %s</p>
                        <div style="background: #e3f2fd; border-left: 4px solid #2196F3; padding: 15px; margin: 15px 0;">
                            <strong>Status:</strong> Backup em andamento...
                        </div>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """,
                task.getId(), type, task.getSourcePath(), task.getDestinationPath(), startedAt
        );
    }

    private String buildScheduledEmail(String backupName, List<String> sources,
                                       List<String> destinations, LocalDateTime nextExecution,
                                       String cronExpression) {
        String nextExec = nextExecution != null ? nextExecution.format(DATE_FORMATTER) : "N/A";
        String sourcesStr = String.join(", ", sources);
        String destinationsStr = String.join(", ", destinations);

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #9C27B0; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Novo Backup Agendado</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>Nome:</strong> %s</p>
                        <p><strong>Origem(s):</strong> %s</p>
                        <p><strong>Destino(s):</strong> %s</p>
                        <p><strong>Expressao Cron:</strong> <code>%s</code></p>
                        <div style="background: #f3e5f5; border-left: 4px solid #9C27B0; padding: 15px; margin: 15px 0;">
                            <strong>Proxima Execucao:</strong><br>%s
                        </div>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """,
                backupName, sourcesStr, destinationsStr, cronExpression, nextExec
        );
    }

    private String buildSuccessEmail(BackupTask task, long durationSeconds) {
        String duration = formatDuration(durationSeconds);
        String size = task.getTotalSizeMB() != null ? task.getTotalSizeMB() + " MB" : "N/A";
        String fileCount = task.getFileCount() != null ? task.getFileCount().toString() : "N/A";
        String finishedAt = task.getFinishedAt() != null ? task.getFinishedAt().format(DATE_FORMATTER) : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #4CAF50; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Backup Concluido com Sucesso</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Origem:</strong> %s</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Concluido em:</strong> %s</p>
                        <p><strong>Duracao:</strong> %s</p>
                        <p><strong>Tamanho:</strong> %s</p>
                        <p><strong>Arquivos:</strong> %s</p>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getSourcePath(), task.getDestinationPath(),
                finishedAt, duration, size, fileCount
        );
    }

    private String buildFailureEmail(BackupTask task, String errorMessage) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #f44336; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Falha no Backup</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Origem:</strong> %s</p>
                        <p><strong>Horario:</strong> %s</p>
                        <div style="background: #ffebee; border-left: 4px solid #f44336; padding: 15px; margin: 15px 0;">
                            <strong>Erro:</strong><br>%s
                        </div>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getSourcePath(), startedAt,
                errorMessage != null ? errorMessage : "Erro desconhecido"
        );
    }

    private String buildCancellationEmail(BackupTask task) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #ff9800; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Backup Cancelado</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Origem:</strong> %s</p>
                        <p><strong>Horario:</strong> %s</p>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getSourcePath(), startedAt
        );
    }

    private String buildRestoreStartedEmail(RestoreTask task) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";
        String restoreType = task.getRestoreType() != null ? task.getRestoreType().name() : "N/A";
        String backupPath = task.getSourceBackup() != null ? task.getSourceBackup().getDestinationPath() : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #1565C0; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Restauracao Iniciada</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Tipo:</strong> %s</p>
                        <p><strong>Backup:</strong> %s</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Iniciada em:</strong> %s</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                task.getId(), restoreType, backupPath, task.getTargetPath(), startedAt
        );
    }

    private String buildRestoreCompletedEmail(RestoreTask task, long durationSeconds) {
        String finishedAt = task.getFinishedAt() != null ? task.getFinishedAt().format(DATE_FORMATTER) : "N/A";
        String restoredFiles = task.getRestoredFiles() != null ? task.getRestoredFiles().toString() : "N/A";
        String size = task.getTotalSizeMB() != null ? task.getTotalSizeMB() + " MB" : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #2E7D32; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Restauracao Concluida</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Concluida em:</strong> %s</p>
                        <p><strong>Duracao:</strong> %s</p>
                        <p><strong>Arquivos restaurados:</strong> %s</p>
                        <p><strong>Tamanho restaurado:</strong> %s</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getTargetPath(), finishedAt, formatDuration(durationSeconds),
                restoredFiles, size
        );
    }

    private String buildRestoreFailureEmail(RestoreTask task, String errorMessage) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #C62828; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Falha na Restauracao</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Iniciada em:</strong> %s</p>
                        <div style="background: #ffebee; border-left: 4px solid #C62828; padding: 15px; margin: 15px 0;">
                            <strong>Erro:</strong><br>%s
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getTargetPath(), startedAt,
                errorMessage != null ? errorMessage : "Erro desconhecido"
        );
    }

    private String buildRestoreCancellationEmail(RestoreTask task) {
        String startedAt = task.getStartedAt() != null ? task.getStartedAt().format(DATE_FORMATTER) : "N/A";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #EF6C00; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Restauracao Cancelada</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <p><strong>ID:</strong> %d</p>
                        <p><strong>Destino:</strong> %s</p>
                        <p><strong>Iniciada em:</strong> %s</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                task.getId(), task.getTargetPath(), startedAt
        );
    }

    private String buildTestEmail() {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #2196F3; color: white; padding: 20px; border-radius: 5px;">
                        <h1>Email de Teste</h1>
                    </div>
                    <div style="background: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px;">
                        <div style="background: #d4edda; border-left: 4px solid #28a745; padding: 15px;">
                            <strong>Configuracao funcionando!</strong><br>
                            Voce recebera notificacoes de backups.
                        </div>
                    </div>
                    <p style="color: #666; font-size: 12px; text-align: center; margin-top: 20px;">
                        Sistema de Backups Automaticos
                    </p>
                </div>
            </body>
            </html>
            """;
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dmin %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dmin %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
