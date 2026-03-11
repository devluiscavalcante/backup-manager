ALTER TABLE scheduled_backups
    ADD COLUMN IF NOT EXISTS last_execution TIMESTAMP;

ALTER TABLE scheduled_backups
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE scheduled_backups
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

COMMENT ON COLUMN scheduled_backups.last_execution IS 'Timestamp da última execução do agendamento';
COMMENT ON COLUMN scheduled_backups.created_at IS 'Timestamp de criação do registro';
COMMENT ON COLUMN scheduled_backups.updated_at IS 'Timestamp da última atualização';

UPDATE scheduled_backups
SET created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;