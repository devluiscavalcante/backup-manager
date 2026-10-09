import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, filter, map } from 'rxjs';
import { CollectionResponse, OperationResponse } from '../api/api.models';
import { AuthService } from '../auth/auth.service';
import { STREAM_OPEN, eventStream } from '../http/event-stream';

export type BackupStatus = 'EM_ANDAMENTO' | 'PAUSADO' | 'CONCLUIDO' | 'FALHA' | 'CANCELADO';

/** Eventos publicados pelo ProgressEmitter do backend em /api/backup/progress. */
export type BackupStreamEvent =
  | { type: 'open' }
  | { type: 'progress'; taskId: number; percent: number; currentFile: string; processedFiles: number; totalFiles: number }
  | { type: 'control'; taskId: number; action: string; status: BackupStatus }
  | { type: 'complete'; message: string }
  | { type: 'error'; message: string };

/** Item de /api/backup/history (BackupResponse). Campos vazios sao omitidos pelo backend. */
export interface BackupRecord {
  sourcePath: string;
  destinationPath: string;
  status: BackupStatus;
  errorMessage?: string;
  fileCount?: number;
  totalSizeMB?: number;
  startedAt?: string;
  finishedAt?: string;
  pausedAt?: string;
  duration?: string;
}

@Injectable({ providedIn: 'root' })
export class BackupService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private readonly apiUrl = '/api/backup';

  startBackup(sources: string[], destinations: string[]): Observable<OperationResponse> {
    // O backend espera o campo "destination" (lista), pareado por indice com "sources".
    return this.http.post<OperationResponse>(`${this.apiUrl}/start`, { sources, destination: destinations });
  }

  progressEvents(): Observable<BackupStreamEvent> {
    const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' };
    const authorization = this.auth.authorizationHeader();
    if (authorization) {
      headers['Authorization'] = authorization;
    }

    return eventStream(`${this.apiUrl}/progress`, headers).pipe(
      map(evt => toBackupEvent(evt.event, evt.data)),
      filter((evt): evt is BackupStreamEvent => evt !== null)
    );
  }

  getHistory(): Observable<BackupRecord[]> {
    return this.http
      .get<CollectionResponse<BackupRecord>>(`${this.apiUrl}/history`)
      .pipe(map(res => res.items));
  }

  pauseBackup(taskId: number): Observable<OperationResponse> {
    return this.http.post<OperationResponse>(`${this.apiUrl}/${taskId}/pause`, {});
  }

  resumeBackup(taskId: number): Observable<OperationResponse> {
    return this.http.post<OperationResponse>(`${this.apiUrl}/${taskId}/resume`, {});
  }

  cancelBackup(taskId: number): Observable<OperationResponse> {
    return this.http.post<OperationResponse>(`${this.apiUrl}/${taskId}/cancel`, {});
  }
}

export function toBackupEvent(event: string, data: string): BackupStreamEvent | null {
  if (event === STREAM_OPEN) {
    return { type: 'open' };
  }

  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(data);
  } catch {
    return null;
  }

  switch (event) {
    case 'progress':
      return {
        type: 'progress',
        taskId: Number(payload['taskId']),
        percent: Number(payload['percent']) || 0,
        currentFile: String(payload['currentFile'] ?? ''),
        processedFiles: Number(payload['processedFiles']) || 0,
        totalFiles: Number(payload['totalFiles']) || 0
      };
    case 'control':
      return {
        type: 'control',
        taskId: Number(payload['taskId']),
        action: String(payload['type'] ?? ''),
        status: payload['status'] as BackupStatus
      };
    case 'complete':
      return { type: 'complete', message: String(payload['message'] ?? '') };
    case 'error':
      return { type: 'error', message: String(payload['error'] ?? '') };
    default:
      return null;
  }
}
