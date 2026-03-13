package com.backup_manager.infrastructure.config;

import com.backup_manager.infrastructure.validation.ValidEmailList;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private boolean enabled = true;
    private Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = true;
        private String from = "backup-system@empresa.com.br";

        @ValidEmailList()
        private List<String> recipients = new ArrayList<>();

        private boolean notifyOnSuccess = true;
        private boolean notifyOnFailure = true;
        private boolean notifyOnCancellation = false;

        private boolean notifyOnStarted = false;
        private boolean notifyOnScheduled = true;
        private boolean notifyOnPaused = false;
        private boolean notifyOnResumed = false;
    }
}