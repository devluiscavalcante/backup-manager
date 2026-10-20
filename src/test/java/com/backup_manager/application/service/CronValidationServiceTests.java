package com.backup_manager.application.service;

import com.backup_manager.application.dto.CronTemplateResponse;
import com.backup_manager.application.dto.CronValidationResponse;
import com.backup_manager.infrastructure.config.BackupSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CronValidationServiceTests {

    private final CronValidationService service = new CronValidationService(new BackupSchedulerProperties());

    @Test
    void acceptsValidExpressionAndListsFiveFutureRuns() {
        LocalDateTime before = LocalDateTime.now();

        CronValidationResponse response = service.validateCronExpression("0 0 2 * * *");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getNextExecutions())
                .hasSize(5)
                .allSatisfy(run -> {
                    assertThat(run).isAfter(before);
                    assertThat(run.getHour()).isEqualTo(2);
                    assertThat(run.getMinute()).isZero();
                })
                .isSorted();
    }

    @Test
    void describesKnownTemplates() {
        assertThat(service.validateCronExpression("0 0 2 * * *").getDescription())
                .isEqualTo("Todos os dias as 2:00 AM");
        assertThat(service.validateCronExpression("0 0 23 L * *").getDescription())
                .isEqualTo("Ultimo dia de cada mes as 23:00");
    }

    @Test
    void describesCustomExpressionsVerbatim() {
        assertThat(service.validateCronExpression("0 30 4 * * *").getDescription())
                .isEqualTo("Expressao cron customizada: 0 30 4 * * *");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankExpression(String expression) {
        CronValidationResponse response = service.validateCronExpression(expression);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Expressao cron nao pode estar vazia");
        assertThat(response.getNextExecutions()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"* * * *", "0 0 25 * * *", "not a cron"})
    void rejectsMalformedExpression(String expression) {
        CronValidationResponse response = service.validateCronExpression(expression);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrorMessage()).startsWith("Expressao cron invalida:");
    }

    @Test
    void returnsEmptyScheduleForMalformedExpression() {
        assertThat(service.calculateNextExecutions("invalid", 3)).isEmpty();
        assertThat(service.calculateNextExecution("invalid")).isNull();
    }

    @Test
    void calculatesRequestedNumberOfRuns() {
        List<LocalDateTime> runs = service.calculateNextExecutions("0 0 * * * *", 3);

        assertThat(runs).hasSize(3);
        assertThat(runs.get(1)).isEqualTo(runs.get(0).plusHours(1));
    }

    @Test
    void everyTemplateIsAValidExpressionDescribedByItsOwnLabel() {
        assertThat(service.getCronTemplates()).isNotEmpty().allSatisfy((key, template) -> {
            assertThat(template.getKey()).isEqualTo(key);
            assertThat(CronExpression.isValidExpression(template.getExpression())).isTrue();
            assertThat(service.validateCronExpression(template.getExpression()).getDescription())
                    .isEqualTo(template.getDescription());
        });
    }

    @Test
    void resolvesExpressionByTemplateKey() {
        CronTemplateResponse daily = service.getCronTemplates().get("daily_2am");

        assertThat(service.getExpressionByTemplateKey("daily_2am")).isEqualTo(daily.getExpression());
        assertThat(service.getExpressionByTemplateKey("unknown")).isNull();
    }
}
