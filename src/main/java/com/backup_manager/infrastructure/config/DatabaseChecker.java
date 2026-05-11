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
            logger.info("Verificando conexao com PostgreSQL...");
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            logger.info("Conexao com PostgreSQL validada.");
        } catch (Exception e) {
            logger.error("Erro na conexao com PostgreSQL: {}", e.getMessage());
            logger.error("Verifique:");
            logger.error("1. PostgreSQL esta rodando");
            logger.error("2. Banco 'backup_manager' existe");
            logger.error("3. Credenciais em application.properties estao corretas");
            throw e;
        }
    }
}
