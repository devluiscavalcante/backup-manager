-- Optimistic locking: pausa, cancelamento e finalizacao concorrentes nao sobrescrevem mais um ao outro.
ALTER TABLE backup_tasks ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
