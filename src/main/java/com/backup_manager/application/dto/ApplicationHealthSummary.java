package com.backup_manager.application.dto;

import com.backup_manager.infrastructure.web.RequestTracingContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;

@Getter
@AllArgsConstructor
public class ApplicationHealthSummary {

    private final boolean publicEndpoint;
    private final String defaultTimeZone;
    private final String requestTracingHeader;

    public static ApplicationHealthSummary current() {
        return new ApplicationHealthSummary(
                true,
                ZoneId.systemDefault().getId(),
                RequestTracingContext.HEADER_NAME
        );
    }
}
