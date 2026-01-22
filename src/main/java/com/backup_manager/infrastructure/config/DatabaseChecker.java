package com.backup_manager.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseChecker implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseChecker.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Verificando conexão com PostgreSQL...");

            // Testa conexão
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            logger.info("Conectado ao PostgreSQL: {}", version.split(",")[0]);

            // Verifica tabela backup_tasks
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'backup_tasks'",
                    Integer.class
            );

            if (count > 0) {
                logger.info("Tabela 'backup_tasks' encontrada");

                // Conta registros
                Integer records = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM backup_tasks",
                        Integer.class
                );
                logger.info("Total de registros na tabela: {}", records);

                // Verifica colunas
                try {
                    Integer pausedColumn = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.columns " +
                                    "WHERE table_name = 'backup_tasks' AND column_name = 'is_paused'",
                            Integer.class
                    );

                    if (pausedColumn > 0) {
                        logger.info("Coluna 'is_paused' existe");
                    } else {
                        logger.warn("Coluna 'is_paused' não encontrada");
                    }

                } catch (Exception e) {
                    logger.warn("Erro ao verificar colunas: {}", e.getMessage());
                }

            } else {
                logger.warn("Tabela 'backup_tasks' não encontrada. Será criada automaticamente.");
            }

        } catch (Exception e) {
            logger.error("ERRO na conexão com PostgreSQL: {}", e.getMessage());
            logger.error("Verifique:");
            logger.error("1. PostgreSQL está rodando");
            logger.error("2. Banco 'backup_manager' existe");
            logger.error("3. Credenciais em application.properties estão corretas");
            throw e;
        }
    }
}