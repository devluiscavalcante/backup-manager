import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { MutationResponse } from '../api/api.models';

export type LogLevel = 'info' | 'warning' | 'error';

/** Linha do warnings.log ja interpretada. */
export interface LogEntry {
  timestamp: string | null;
  level: LogLevel;
  message: string;
}

/** Payload de GET /api/logs/warnings (LogContentResponse). */
interface LogContent {
  logType: string;
  content?: string;
  message?: string;
  timestamp?: string;
}

export interface WarningsLog {
  raw: string;
  entries: LogEntry[];
}

@Injectable({ providedIn: 'root' })
export class LogsService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/logs';

  /** Le o warnings.log do backup mais recente. Responde 404 quando nenhum log foi gerado ainda. */
  getWarningsLog(): Observable<WarningsLog> {
    return this.http.get<MutationResponse<LogContent>>(`${this.apiUrl}/warnings`).pipe(
      map(res => {
        const raw = res.data.content ?? '';
        return { raw, entries: parseLogLines(raw) };
      })
    );
  }
}

// Formato gravado pelo BackupService: "[2026-01-01T10:00:00.123] [WARNING] mensagem: caminho"
const LOG_LINE = /^\[([^\]]+)\]\s+\[([A-Z]+)\]\s+(.*)$/;

export function parseLogLines(content: string): LogEntry[] {
  return content
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .map(line => {
      const match = LOG_LINE.exec(line);
      if (!match) {
        return { timestamp: null, level: 'info' as LogLevel, message: line };
      }
      return { timestamp: match[1], level: toLevel(match[2]), message: match[3] };
    });
}

function toLevel(raw: string): LogLevel {
  if (raw.startsWith('ERR') || raw === 'FATAL') return 'error';
  if (raw.startsWith('WARN')) return 'warning';
  return 'info';
}
