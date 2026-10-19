package com.backup_manager.infrastructure.config;

import com.backup_manager.domain.model.Status;
import com.backup_manager.domain.model.BackupTask;
import com.backup_manager.infrastructure.persistence.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BackupCleanupRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BackupCleanupRunner.class);
    private static final String INTERRUPTED_MESSAGE = "Interrompido devido ao reinício ou fechamento da aplicação";

    private final BackupRepository repository;

    public BackupCleanupRunner(BackupRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("Iniciando verificação de integridade pós-boot");

        List<BackupTask> orphanTasks = repository.findByStatusIn(List.of(Status.EM_ANDAMENTO, Status.PAUSADO));

        if (orphanTasks.isEmpty()) {
            logger.info("Nenhuma tarefa órfã encontrada.");
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

        logger.info("Verificação de integridade concluída.");
    }
}
