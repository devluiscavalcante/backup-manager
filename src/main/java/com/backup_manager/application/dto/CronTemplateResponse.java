package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CronTemplateResponse {
    private String key;
    private String expression;
    private String description;
    private String category;

    public static class CronTemplatesMap {
        private Map<String, CronTemplateResponse> templates;

        public CronTemplatesMap(Map<String, CronTemplateResponse> templates) {
            this.templates = templates;
        }

        public Map<String, CronTemplateResponse> getTemplates() {
            return templates;
        }
    }
}