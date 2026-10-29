package com.backup_manager.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Encerramento ordenado dos pools de backup e restauracao. Os executores so esperam as tarefas no
 * destroy, quando o pool do banco pode ja ter fechado e o worker nao consegue gravar o resultado.
 * Aqui a espera acontece numa fase do ciclo de vida, depois de o servidor web parar de aceitar
 * requisicoes e antes de qualquer bean ser destruido.
 * Tarefas que passarem do prazo sao cortadas com o processo e viram FALHA (com notificacao) no proximo boot.
 */
@Component
public class TaskShutdownCoordinator implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(TaskShutdownCoordinator.class);

    // Abaixo das fases do servidor web (graceful shutdown e parada do conector).
    static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

    private final ThreadPoolTaskScheduler oneTimeScheduler;
    private final ThreadPoolTaskExecutor dispatchExecutor;
    private final List<ThreadPoolTaskExecutor> workerExecutors;
    private final Duration timeout;
    private volatile boolean running;

    public TaskShutdownCoordinator(@Qualifier("backupOneTimeScheduler") ThreadPoolTaskScheduler oneTimeScheduler,
                                   @Qualifier("backupDispatchExecutor") ThreadPoolTaskExecutor dispatchExecutor,
                                   @Qualifier("backupTaskExecutor") ThreadPoolTaskExecutor backupTaskExecutor,
                                   @Qualifier("restoreTaskExecutor") ThreadPoolTaskExecutor restoreTaskExecutor,
                                   @Value("${spring.lifecycle.timeout-per-shutdown-phase:30s}") Duration timeout) {
        this.oneTimeScheduler = oneTimeScheduler;
        this.dispatchExecutor = dispatchExecutor;
        this.workerExecutors = List.of(backupTaskExecutor, restoreTaskExecutor);
        this.timeout = timeout;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        long deadline = System.nanoTime() + timeout.toNanos();

        // Disparos futuros sao descartados: execucoes unicas e crons voltam do banco no proximo boot.
        oneTimeScheduler.getScheduledThreadPoolExecutor().setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        awaitAfterShutdown(oneTimeScheduler.getScheduledExecutor(), "backupOneTimeScheduler", deadline);

        // O disparo ainda entrega tarefas aos workers: so depois de esvazia-lo os workers param de aceitar.
        awaitAfterShutdown(dispatchExecutor.getThreadPoolExecutor(), "backupDispatchExecutor", deadline);

        workerExecutors.forEach(executor -> executor.getThreadPoolExecutor().shutdown());
        for (ThreadPoolTaskExecutor executor : workerExecutors) {
            awaitAfterShutdown(executor.getThreadPoolExecutor(), executor.getThreadNamePrefix(), deadline);
        }
    }

    private void awaitAfterShutdown(ExecutorService executor, String name, long deadline) {
        executor.shutdown();
        try {
            long remaining = Math.max(0, deadline - System.nanoTime());
            if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                return;
            }
            logger.warn("Encerramento: {} ainda tem tarefas em andamento apos {}; serao marcadas como "
                    + "FALHA no proximo boot", name, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Encerramento interrompido aguardando {}", name);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // Uma pausa do contexto nao pode desligar os pools: eles nao voltam no restart.
    @Override
    public boolean isPauseable() {
        return false;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
