-- Agendamentos unicos persistidos: antes ficavam so em memoria e sumiam a cada restart.
CREATE TABLE IF NOT EXISTS one_time_backups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS one_time_backup_sources (
    one_time_backup_id BIGINT NOT NULL,
    source_path VARCHAR(1000),
    CONSTRAINT fk_one_time_backup_sources
        FOREIGN KEY (one_time_backup_id) REFERENCES one_time_backups(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS one_time_backup_destinations (
    one_time_backup_id BIGINT NOT NULL,
    destination_path VARCHAR(1000),
    CONSTRAINT fk_one_time_backup_destinations
        FOREIGN KEY (one_time_backup_id) REFERENCES one_time_backups(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_one_time_backups_status ON one_time_backups(status);
CREATE INDEX IF NOT EXISTS idx_one_time_backup_sources_backup ON one_time_backup_sources(one_time_backup_id);
CREATE INDEX IF NOT EXISTS idx_one_time_backup_destinations_backup ON one_time_backup_destinations(one_time_backup_id);
