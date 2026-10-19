package com.backup_manager.application.service;

import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CronValidationService {

    private static final Logger logger = LoggerFactory.getLogger(CronValidationService.class);
    private static final int DEFAULT_NEXT_EXECUTIONS_COUNT = 5;

    private static final List<CronTemplateResponse> TEMPLATES = List.of(
            template("daily_2am", "0 0 2 * * *", "Todos os dias as 2:00 AM", "Diario"),
            template("daily_midnight", "0 0 0 * * *", "Todos os dias a meia-noite", "Diario"),
            template("daily_noon", "0 0 12 * * *", "Todos os dias ao meio-dia", "Diario"),
            template("weekly_sunday_3am", "0 0 3 ? * SUN", "Toda semana aos domingos as 3:00 AM", "Semanal"),
            template("weekly_monday_2am", "0 0 2 ? * MON", "Toda semana as segundas-feiras as 2:00 AM", "Semanal"),
            template("weekly_friday_6pm", "0 0 18 ? * FRI", "Toda semana as sextas-feiras as 18:00", "Semanal"),
            template("monthly_first_midnight", "0 0 0 1 * *", "Primeiro dia de cada mes a meia-noite", "Mensal"),
            template("monthly_last_11pm", "0 0 23 L * *", "Ultimo dia de cada mes as 23:00", "Mensal"),
            template("every_6_hours", "0 0 */6 * * *", "A cada 6 horas", "Intervalo"),
            template("every_12_hours", "0 0 */12 * * *", "A cada 12 horas", "Intervalo"),
            template("every_hour", "0 0 * * * *", "A cada hora", "Intervalo")
    );

    public CronValidationResponse validateCronExpression(String cronExpression) {
        logger.debug("Validando expressao cron: {}", cronExpression);

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return CronValidationResponse.of(
                    false,
                    null,
                    "Expressao cron nao pode estar vazia",
                    null
            );
        }

        try {
            CronExpression cron = CronExpression.parse(cronExpression);

            List<LocalDateTime> nextExecutions = calculateNextExecutions(cron, DEFAULT_NEXT_EXECUTIONS_COUNT);
            String description = generateDescription(cronExpression);

            logger.info("Expressao cron valida: {} - {}", cronExpression, description);

            return CronValidationResponse.of(
                    true,
                    description,
                    null,
                    nextExecutions
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Expressao cron invalida: {} - Erro: {}", cronExpression, e.getMessage());

            return CronValidationResponse.of(
                    false,
                    null,
                    "Expressao cron invalida: " + e.getMessage(),
                    null
            );
        }
    }

    public List<LocalDateTime> calculateNextExecutions(String cronExpression, int count) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            return calculateNextExecutions(cron, count);
        } catch (IllegalArgumentException e) {
            logger.error("Erro ao calcular proximas execucoes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public LocalDateTime calculateNextExecution(String cronExpression) {
        List<LocalDateTime> executions = calculateNextExecutions(cronExpression, 1);
        return executions.isEmpty() ? null : executions.getFirst();
    }

    public Map<String, CronTemplateResponse> getCronTemplates() {
        Map<String, CronTemplateResponse> templates = new LinkedHashMap<>();
        TEMPLATES.forEach(template -> templates.put(template.getKey(), template));
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
        return TEMPLATES.stream()
                .filter(template -> template.getExpression().equals(cronExpression))
                .map(CronTemplateResponse::getDescription)
                .findFirst()
                .orElse("Expressao cron customizada: " + cronExpression);
    }

    private static CronTemplateResponse template(String key, String expression, String description, String category) {
        return new CronTemplateResponse(key, expression, description, category);
    }

    public String getExpressionByTemplateKey(String templateKey) {
        Map<String, CronTemplateResponse> templates = getCronTemplates();
        CronTemplateResponse template = templates.get(templateKey);
        return template != null ? template.getExpression() : null;
    }
}
