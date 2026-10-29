package com.backup_manager.infrastructure.config;

import com.backup_manager.domain.event.BackupFailedEvent;
import com.backup_manager.domain.event.RestoreFailedEvent;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.domain.model.RestoreStatus;
import com.backup_manager.domain.model.RestoreTask;
import com.backup_manager.domain.model.Status;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import com.backup_manager.infrastructure.persistence.RestoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BackupCleanupRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BackupCleanupRunner.class);
    private static final String INTERRUPTED_MESSAGE = "Interrompido devido ao reinício ou fechamento da aplicação";

    private final BackupRepository repository;
    private final RestoreRepository restoreRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BackupCleanupRunner(BackupRepository repository, RestoreRepository restoreRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.restoreRepository = restoreRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("Iniciando verificação de integridade pós-boot");

        failInterruptedBackups();
        failInterruptedRestores();

        logger.info("Verificação de integridade concluída.");
    }

    private void failInterruptedBackups() {
        List<BackupTask> orphanTasks = repository.findByStatusIn(List.of(Status.EM_ANDAMENTO, Status.PAUSADO));

        if (orphanTasks.isEmpty()) {
            logger.info("Nenhum backup órfão encontrado.");
            return;
        }

        logger.warn("Detectadas {} tarefas interrompidas.", orphanTasks.size());

        LocalDateTime now = LocalDateTime.now();
        orphanTasks.forEach(task -> {
            task.setStatus(Status.FALHA);
            task.setErrorMessage(INTERRUPTED_MESSAGE);
            task.setFinishedAt(now);
            logger.debug("Tarefa ID {} marcada como falha", task.getId());
        });
        repository.saveAll(orphanTasks);
        // Sem isso a interrupcao por restart/deploy passava em silencio: o worker morto nao publica nada.
        orphanTasks.forEach(task -> eventPublisher.publishEvent(new BackupFailedEvent(task, INTERRUPTED_MESSAGE)));
    }

    // Restauracoes rodam em memoria como os backups: um restart deixa a tarefa EM_ANDAMENTO para sempre.
    private void failInterruptedRestores() {
        List<RestoreTask> orphanRestores = restoreRepository.findByStatus(RestoreStatus.EM_ANDAMENTO);

        if (orphanRestores.isEmpty()) {
            logger.info("Nenhuma restauração órfã encontrada.");
            return;
        }

        logger.warn("Detectadas {} restaurações interrompidas.", orphanRestores.size());

        LocalDateTime now = LocalDateTime.now();
        orphanRestores.forEach(task -> {
            task.setStatus(RestoreStatus.FALHA);
            task.setErrorMessage(INTERRUPTED_MESSAGE);
            task.setFinishedAt(now);
        });
        restoreRepository.saveAll(orphanRestores);
        orphanRestores.forEach(task -> eventPublisher.publishEvent(new RestoreFailedEvent(task, INTERRUPTED_MESSAGE)));
    }
}
