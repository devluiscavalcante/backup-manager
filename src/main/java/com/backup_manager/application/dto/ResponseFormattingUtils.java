package com.backup_manager.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class ResponseFormattingUtils {

    private ResponseFormattingUtils() {
    }

    public static String formatDuration(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return "";
        }

        long seconds = Duration.between(startedAt, finishedAt).getSeconds();
        return String.format("%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public static BigDecimal normalizeSize(BigDecimal sizeMB) {
        return Objects.requireNonNullElse(sizeMB, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
