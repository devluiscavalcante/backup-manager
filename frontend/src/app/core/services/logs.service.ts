import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Log {
  id?: number;
  timestamp: string;
  level: 'info' | 'success' | 'warning' | 'error';
  message: string;
  source: string;
}

@Injectable({
  providedIn: 'root'
})
export class LogsService {
  private apiUrl = 'http://localhost:8080/api/logs';

  constructor(private http: HttpClient) {}

  getLogs(): Observable<Log[]> {
    return this.http.get<Log[]>(this.apiUrl);
  }
}
