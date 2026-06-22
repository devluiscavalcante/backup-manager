package com.backup_manager.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationVersionServiceTests {

    @Test
    void shouldReturnConfiguredVersionWhenAvailable() {
        ApplicationVersionService service = new ApplicationVersionService("2.4.1");

        assertEquals("2.4.1", service.currentVersion());
    }

    @Test
    void shouldReturnDevelopmentWhenVersionMetadataIsUnavailable() {
        ApplicationVersionService service = new ApplicationVersionService("");

        assertEquals("development", service.currentVersion());
    }
}
