package com.backup_manager.infrastructure.logging;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class BackupContext {

    private final AtomicReference<String> lastDestination = new AtomicReference<>();

    public void setLastDestination(String path) {
        lastDestination.set(path);
    }

    public String getLastDestination() {
        return lastDestination.get();
    }
}
