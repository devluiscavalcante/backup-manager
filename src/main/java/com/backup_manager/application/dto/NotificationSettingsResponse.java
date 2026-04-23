package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationSettingsResponse {

    private boolean notificationsEnabled;
    private boolean emailEnabled;
    private String fromAddress;
    private int recipientsCount;
    private boolean notifyOnSuccess;
    private boolean notifyOnFailure;
    private boolean notifyOnCancellation;
    private boolean notifyOnStarted;
    private boolean notifyOnScheduled;
}
