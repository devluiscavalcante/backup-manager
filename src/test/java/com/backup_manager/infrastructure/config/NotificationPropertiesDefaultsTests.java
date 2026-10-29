package com.backup_manager.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPropertiesDefaultsTests {

    @Test
    void failuresShouldBeNotifiedWhenOnlyNotificationsAreEnabled() throws IOException {
        NotificationProperties.Email email = bind(Map.of("NOTIFICATION_ENABLED", "true"));

        assertThat(email.isNotifyOnFailure()).isTrue();
        assertThat(email.isNotifyOnCancellation()).isTrue();
        assertThat(email.isNotifyOnSuccess()).isFalse();
        assertThat(email.isNotifyOnStarted()).isFalse();
        assertThat(email.isNotifyOnScheduled()).isFalse();
    }

    @Test
    void everyNotificationTypeShouldBeConfigurableByEnvironment() throws IOException {
        NotificationProperties.Email email = bind(Map.of(
                "NOTIFICATION_NOTIFY_SUCCESS", "true",
                "NOTIFICATION_NOTIFY_FAILURE", "false",
                "NOTIFICATION_NOTIFY_CANCELLATION", "false",
                "NOTIFICATION_NOTIFY_STARTED", "true",
                "NOTIFICATION_NOTIFY_SCHEDULED", "true"
        ));

        assertThat(email.isNotifyOnSuccess()).isTrue();
        assertThat(email.isNotifyOnFailure()).isFalse();
        assertThat(email.isNotifyOnCancellation()).isFalse();
        assertThat(email.isNotifyOnStarted()).isTrue();
        assertThat(email.isNotifyOnScheduled()).isTrue();
    }

    // So o application.properties e as variaveis do teste: o ambiente da maquina/CI nao interfere.
    private static NotificationProperties.Email bind(Map<String, Object> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("env", env));
        List<PropertySource<?>> loaded = new PropertiesPropertySourceLoader()
                .load("application", new ClassPathResource("application.properties"));
        loaded.forEach(sources::addLast);

        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(environment));
        return binder.bind("notification", NotificationProperties.class)
                .get()
                .getEmail();
    }
}
