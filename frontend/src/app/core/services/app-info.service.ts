import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';

interface HealthStatusResponse {
  status: string;
  service: string;
  version?: string;
}

/** Versao exibida na interface, lida do endpoint publico do backend (fonte unica: pom.xml). */
@Injectable({ providedIn: 'root' })
export class AppInfoService {
  private http = inject(HttpClient);

  readonly version = toSignal(
    this.http.get<HealthStatusResponse>('/api/health/application').pipe(
      map(res => res.version || null),
      catchError(() => of(null))
    ),
    { initialValue: null }
  );
}
