package com.backup_manager.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TaskShutdownCoordinatorTests {

    @Test
    void contextCloseShouldWaitForRunningBackupBeforeDestroyingBeans() throws Exception {
        AnnotationConfigApplicationContext context = context("30s");
        FakeDataSource dataSource = context.getBean(FakeDataSource.class);
        AtomicBoolean finishedWithDatabaseOpen = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);

        context.getBean("backupTaskExecutor", ThreadPoolTaskExecutor.class).execute(() -> {
            started.countDown();
            sleep(Duration.ofMillis(500));
            finishedWithDatabaseOpen.set(!dataSource.closed.get());
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        context.close();

        // O worker terminou e ainda conseguiria gravar o resultado: o "banco" so fechou depois.
        assertThat(finishedWithDatabaseOpen).isTrue();
        assertThat(dataSource.closed).isTrue();
    }

    @Test
    void contextCloseShouldGiveUpAfterConfiguredTimeout() throws Exception {
        AnnotationConfigApplicationContext context = context("300ms");
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        context.getBean("restoreTaskExecutor", ThreadPoolTaskExecutor.class).execute(() -> {
            started.countDown();
            awaitQuietly(release);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        long begin = System.nanoTime();
        try {
            context.close();
        } finally {
            release.countDown();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void contextCloseShouldDiscardFutureTriggersInsteadOfWaitingForThem() {
        AnnotationConfigApplicationContext context = context("30s");
        AtomicBoolean ran = new AtomicBoolean();

        context.getBean("backupOneTimeScheduler", ThreadPoolTaskScheduler.class)
                .schedule(() -> ran.set(true), Instant.now().plusSeconds(3600));

        long begin = System.nanoTime();
        context.close();

        assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(5));
        assertThat(ran).isFalse();
    }

    private static AnnotationConfigApplicationContext context(String timeout) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("spring.lifecycle.timeout-per-shutdown-phase", timeout)));
        context.register(AsyncConfig.class, TaskShutdownCoordinator.class, FakeDataSource.class);
        context.refresh();
        return context;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Faz o papel do pool do banco: so fecha na destruicao dos beans. */
    static class FakeDataSource implements DisposableBean {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void destroy() {
            closed.set(true);
        }
    }
}
