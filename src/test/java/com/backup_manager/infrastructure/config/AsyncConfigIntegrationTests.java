package com.backup_manager.infrastructure.config;

import com.backup_manager.application.service.EmailNotificationService;
import com.backup_manager.application.service.RestoreService;
import com.backup_manager.domain.event.BackupStartedEvent;
import com.backup_manager.domain.model.BackupTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "app.security.allow-default-password=true")
@ActiveProfiles("test")
class AsyncConfigIntegrationTests {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AsyncConfig asyncConfig;

    @MockitoBean
    private EmailNotificationService emailNotificationService;

    @Test
    void unqualifiedAsyncListenersShouldRunOnBoundedPool() throws Exception {
        CompletableFuture<String> threadName = new CompletableFuture<>();
        doAnswer(invocation -> {
            threadName.complete(Thread.currentThread().getName());
            return null;
        }).when(emailNotificationService).sendStartedNotification(any(), anyBoolean());

        eventPublisher.publishEvent(new BackupStartedEvent(new BackupTask(), false));

        assertThat(threadName.get(5, TimeUnit.SECONDS)).startsWith("async-");
    }

    @Test
    void defaultExecutorShouldFallBackToCallerWhenSaturated() {
        assertThat(asyncConfig.getAsyncExecutor()).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.getAsyncExecutor();
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    void restoreShouldUseDedicatedExecutor() throws Exception {
        Method method = RestoreService.class.getMethod(
                "processRestoreAsync", com.backup_manager.domain.model.RestoreTask.class, boolean.class, List.class);

        assertThat(method.getAnnotation(Async.class).value()).isEqualTo("restoreTaskExecutor");
    }

    @Test
    void uncaughtAsyncFailuresShouldBeLoggedWithoutPropagating() throws Exception {
        Method method = EmailNotificationService.class.getMethod("sendTestEmail");

        assertThatCode(() -> asyncConfig.getAsyncUncaughtExceptionHandler()
                .handleUncaughtException(new IllegalStateException("smtp down"), method))
                .doesNotThrowAnyException();
    }
}
