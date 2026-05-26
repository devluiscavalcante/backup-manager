package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class CronTemplatesResponse {

    private final Map<String, CronTemplateResponse> templates;
}
