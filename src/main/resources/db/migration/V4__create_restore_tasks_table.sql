CREATE TABLE restore_tasks (
                               id BIGSERIAL PRIMARY KEY,
                               backup_task_id BIGINT NOT NULL,

                               target_path VARCHAR(500) NOT NULL,
                               restore_type VARCHAR(20) NOT NULL,
                               selected_files TEXT,

                               status VARCHAR(20) NOT NULL,
                               started_at TIMESTAMP,
                               finished_at TIMESTAMP,

                               file_count BIGINT,
                               total_size_mb DECIMAL(10,2),
                               restored_files BIGINT DEFAULT 0,

                               error_message TEXT,
                               is_cancelled BOOLEAN DEFAULT FALSE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_restore_backup FOREIGN KEY (backup_task_id)
                                   REFERENCES backup_tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_restore_backup_id ON restore_tasks(backup_task_id);
CREATE INDEX idx_restore_status ON restore_tasks(status);
CREATE INDEX idx_restore_started_at ON restore_tasks(started_at DESC);
CREATE INDEX idx_restore_created_at ON restore_tasks(created_at DESC);

COMMENT ON TABLE restore_tasks IS 'Histórico de restaurações de backups';
COMMENT ON COLUMN restore_tasks.backup_task_id IS 'ID que está sendo restaurado';
COMMENT ON COLUMN restore_tasks.target_path IS 'Caminho de destino da restauração';
COMMENT ON COLUMN restore_tasks.restore_type IS 'FULL ou SELECTIVE';
COMMENT ON COLUMN restore_tasks.selected_files IS 'JSON array de arquivos selecionados (apenas SELECTIVE)';
COMMENT ON COLUMN restore_tasks.restored_files IS 'Progresso de arquivos restaurados';