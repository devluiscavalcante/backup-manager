package com.backup_manager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "backup.scheduler")
public class BackupSchedulerProperties {

    private boolean enabled = false;
    private String cronExpression = "0 0 2 * * *";
    private String timeZone = "America/Sao_Paulo";
    private List<ScheduledBackupConfig> scheduledBackups = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public List<ScheduledBackupConfig> getScheduledBackups() {
        return scheduledBackups;
    }

    public void setScheduledBackups(List<ScheduledBackupConfig> scheduledBackups) {
        this.scheduledBackups = scheduledBackups;
    }

    public static class ScheduledBackupConfig {
        private String name;
        private List<String> sources;
        private List<String> destinations;
        private boolean enabled = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getSources() {
            return sources;
        }

        public void setSources(List<String> sources) {
            this.sources = sources;
        }

        public List<String> getDestinations() {
            return destinations;
        }

        public void setDestinations(List<String> destinations) {
            this.destinations = destinations;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}