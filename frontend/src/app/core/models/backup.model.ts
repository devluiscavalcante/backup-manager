export interface BackupTask{
  id: number;
  name: string;
  date: string;
  size: string;
  status: 'in_progress' | 'paused' | 'completed' | 'cancelled' | 'failed' | 'unknown';
  files: number;
  duration: string;
  source: string;
  destination: string;
  errorMessage?: string;
}

export interface LogEntry{
  id: number;
  name: string;
  level: 'success' | 'warning' | 'error' | 'info';
  message: string;
  source: string;
}
