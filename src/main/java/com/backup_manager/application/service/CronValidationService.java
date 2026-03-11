package com.backup_manager.application.service;

import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CronValidationService {

    private static final Logger logger = LoggerFactory.getLogger(CronValidationService.class);
    private static final int DEFAULT_NEXT_EXECUTIONS_COUNT = 5;

    public CronValidationResponse validateCronExpression(String cronExpression) {
        logger.debug("Validando expressão cron: {}", cronExpression);

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return new CronValidationResponse(
                    false,
                    null,
                    "Expressão cron não pode estar vazia",
                    null
            );
        }

        try {
            CronExpression cron = CronExpression.parse(cronExpression);

            List<LocalDateTime> nextExecutions = calculateNextExecutions(cron, DEFAULT_NEXT_EXECUTIONS_COUNT);

            String description = generateDescription(cronExpression);

            logger.info("Expressão cron válida: {} - {}", cronExpression, description);

            return new CronValidationResponse(
                    true,
                    description,
                    null,
                    nextExecutions
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Expressão cron inválida: {} - Erro: {}", cronExpression, e.getMessage());

            return new CronValidationResponse(
                    false,
                    null,
                    "Expressão cron inválida: " + e.getMessage(),
                    null
            );
        }
    }

    public List<LocalDateTime> calculateNextExecutions(String cronExpression, int count) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            return calculateNextExecutions(cron, count);
        } catch (IllegalArgumentException e) {
            logger.error("Erro ao calcular próximas execuções: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public LocalDateTime calculateNextExecution(String cronExpression) {
        List<LocalDateTime> executions = calculateNextExecutions(cronExpression, 1);
        return executions.isEmpty() ? null : executions.getFirst();
    }

    public Map<String, CronTemplateResponse> getCronTemplates() {
        Map<String, CronTemplateResponse> templates = new LinkedHashMap<>();

        templates.put("daily_2am", new CronTemplateResponse(
                "daily_2am",
                "0 0 2 * * *",
                "Todos os dias às 2:00 AM",
                "Diário"
        ));

        templates.put("daily_midnight", new CronTemplateResponse(
                "daily_midnight",
                "0 0 0 * * *",
                "Todos os dias à meia-noite",
                "Diário"
        ));

        templates.put("daily_noon", new CronTemplateResponse(
                "daily_noon",
                "0 0 12 * * *",
                "Todos os dias ao meio-dia",
                "Diário"
        ));

        templates.put("weekly_sunday_3am", new CronTemplateResponse(
                "weekly_sunday_3am",
                "0 0 3 ? * SUN",
                "Toda semana aos domingos às 3:00 AM",
                "Semanal"
        ));

        templates.put("weekly_monday_2am", new CronTemplateResponse(
                "weekly_monday_2am",
                "0 0 2 ? * MON",
                "Toda semana às segundas-feiras às 2:00 AM",
                "Semanal"
        ));

        templates.put("weekly_friday_6pm", new CronTemplateResponse(
                "weekly_friday_6pm",
                "0 0 18 ? * FRI",
                "Toda semana às sextas-feiras às 18:00",
                "Semanal"
        ));

        templates.put("monthly_first_midnight", new CronTemplateResponse(
                "monthly_first_midnight",
                "0 0 0 1 * *",
                "Primeiro dia de cada mês à meia-noite",
                "Mensal"
        ));

        templates.put("monthly_last_11pm", new CronTemplateResponse(
                "monthly_last_11pm",
                "0 0 23 L * *",
                "Último dia de cada mês às 23:00",
                "Mensal"
        ));

        templates.put("every_6_hours", new CronTemplateResponse(
                "every_6_hours",
                "0 0 */6 * * *",
                "A cada 6 horas",
                "Intervalo"
        ));

        templates.put("every_12_hours", new CronTemplateResponse(
                "every_12_hours",
                "0 0 */12 * * *",
                "A cada 12 horas",
                "Intervalo"
        ));

        templates.put("every_hour", new CronTemplateResponse(
                "every_hour",
                "0 0 * * * *",
                "A cada hora",
                "Intervalo"
        ));

        return templates;
    }

    private List<LocalDateTime> calculateNextExecutions(CronExpression cron, int count) {
        List<LocalDateTime> executions = new ArrayList<>();
        LocalDateTime next = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            next = cron.next(next);
            if (next == null) {
                break;
            }
            executions.add(next);
        }

        return executions;
    }

    private String generateDescription(String cronExpression) {
        Map<String, String> commonPatterns = new HashMap<>();
        commonPatterns.put("0 0 0 * * *", "Todos os dias à meia-noite");
        commonPatterns.put("0 0 2 * * *", "Todos os dias às 2:00 AM");
        commonPatterns.put("0 0 12 * * *", "Todos os dias ao meio-dia");
        commonPatterns.put("0 0 3 ? * SUN", "Toda semana aos domingos às 3:00 AM");
        commonPatterns.put("0 0 2 ? * MON", "Toda semana às segundas-feiras às 2:00 AM");
        commonPatterns.put("0 0 18 ? * FRI", "Toda semana às sextas-feiras às 18:00");
        commonPatterns.put("0 0 0 1 * *", "Primeiro dia de cada mês à meia-noite");
        commonPatterns.put("0 0 23 L * *", "Último dia de cada mês às 23:00");
        commonPatterns.put("0 0 */6 * * *", "A cada 6 horas");
        commonPatterns.put("0 0 */12 * * *", "A cada 12 horas");
        commonPatterns.put("0 0 * * * *", "A cada hora");

        if (commonPatterns.containsKey(cronExpression)) {
            return commonPatterns.get(cronExpression);
        }

        return "Expressão cron customizada: " + cronExpression;
    }

    public String getExpressionByTemplateKey(String templateKey) {
        Map<String, CronTemplateResponse> templates = getCronTemplates();
        CronTemplateResponse template = templates.get(templateKey);
        return template != null ? template.getExpression() : null;
    }
}