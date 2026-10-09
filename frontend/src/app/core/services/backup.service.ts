import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BackupProgress {
  percentage: number;
  currentFile?: string;
  taskId?: number;
}

export interface BackupRecord {
  sourcePath: string;
  destinationPath: string;
  status: string;
  errorMessage?: string;
  fileCount: number;
  totalSizeMB: number;
  startedAt: string;
  finishedAt: string;
  duration: string;
}

@Injectable({
  providedIn: 'root'
})
export class BackupService {
  private apiUrl = 'http://localhost:8080/api/backup';

  constructor(private http: HttpClient, private zone: NgZone) {}

  loadHistory(): void {
    console.log('BackupService: Motor de sincronização e auditoria operacional.');
  }

  startBackup(sources: string[], destinations: string[]): Observable<any> {
    const payload = { sources, destination: destinations };
    return this.http.post<any>(`${this.apiUrl}/start`, payload);
  }

  getRealTimeProgress(): Observable<BackupProgress> {
    return new Observable<BackupProgress>(observer => {
      const eventSource = new EventSource(`${this.apiUrl}/progress`);

      eventSource.onmessage = (event) => {
        this.zone.run(() => {
          try {
            const data: BackupProgress = JSON.parse(event.data);
            observer.next(data);
          } catch (e) {
            console.error('Falha no parse do stream de progresso:', e);
          }
        });
      };

      eventSource.onerror = (error) => {
        this.zone.run(() => {
          observer.error(error);
          eventSource.close();
        });
      };

      return () => eventSource.close();
    });
  }

  getHistory(): Observable<BackupRecord[]> {
    return this.http.get<BackupRecord[]>(`${this.apiUrl}/history`);
  }

  pauseBackup(taskId: number): Observable<string> {
    return this.http.post(`${this.apiUrl}/${taskId}/pause`, {}, { responseType: 'text' });
  }

  resumeBackup(taskId: number): Observable<string> {
    return this.http.post(`${this.apiUrl}/${taskId}/resume`, {}, { responseType: 'text' });
  }

  cancelBackup(taskId: number): Observable<string> {
    return this.http.post(`${this.apiUrl}/${taskId}/cancel`, {}, { responseType: 'text' });
  }
}
