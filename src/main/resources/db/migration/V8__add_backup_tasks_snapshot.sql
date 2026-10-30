-- Snapshot por execucao: cada backup grava a propria pasta e o historico aponta para ela. Nulo nos backups
-- antigos, que gravavam por cima de um espelho unico na raiz do destino.
ALTER TABLE backup_tasks ADD COLUMN IF NOT EXISTS snapshot_path VARCHAR(1000);
-- Preenchido quando a retencao remove o snapshot: a entrada continua no historico, mas nao restaura.
ALTER TABLE backup_tasks ADD COLUMN IF NOT EXISTS snapshot_removed_at TIMESTAMP;
