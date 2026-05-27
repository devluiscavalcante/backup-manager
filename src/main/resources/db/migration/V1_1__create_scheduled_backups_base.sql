CREATE TABLE IF NOT EXISTS scheduled_backups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_execution TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scheduled_backup_sources (
    scheduled_backup_id BIGINT NOT NULL,
    source_path VARCHAR(1000),
    CONSTRAINT fk_scheduled_backup_sources_config
        FOREIGN KEY (scheduled_backup_id) REFERENCES scheduled_backups(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS scheduled_backup_destinations (
    scheduled_backup_id BIGINT NOT NULL,
    destination_path VARCHAR(1000),
    CONSTRAINT fk_scheduled_backup_destinations_config
        FOREIGN KEY (scheduled_backup_id) REFERENCES scheduled_backups(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_scheduled_backups_enabled ON scheduled_backups(enabled);
CREATE INDEX IF NOT EXISTS idx_scheduled_backup_sources_config ON scheduled_backup_sources(scheduled_backup_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_backup_destinations_config ON scheduled_backup_destinations(scheduled_backup_id);
