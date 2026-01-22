CREATE TABLE backup_tasks
(
    id               BIGSERIAL PRIMARY KEY,
    source_path      VARCHAR(1000) NOT NULL,
    destination_path VARCHAR(1000) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    error_message    TEXT,
    file_count       BIGINT,
    total_size_mb    NUMERIC(10, 2),
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP,
    paused_at        TIMESTAMP,
    is_paused        BOOLEAN DEFAULT FALSE,
    is_cancelled     BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_backup_tasks_status ON backup_tasks (status);
CREATE INDEX idx_backup_tasks_source_dest ON backup_tasks (source_path, destination_path);