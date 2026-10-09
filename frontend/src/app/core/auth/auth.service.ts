import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

const STORAGE_KEY = 'bkm.auth';

interface StoredCredentials {
  username: string;
  token: string;
}

/**
 * Guarda as credenciais HTTP Basic da sessao do navegador.
 * O backend e stateless: cada requisicao precisa reenviar o cabecalho Authorization.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private credentials = signal<StoredCredentials | null>(readStored());

  readonly isAuthenticated = computed(() => this.credentials() !== null);
  readonly username = computed(() => this.credentials()?.username ?? null);

  /** Valor pronto para o cabecalho Authorization, ou null sem sessao. */
  authorizationHeader(): string | null {
    const current = this.credentials();
    return current ? `Basic ${current.token}` : null;
  }

  /** Valida as credenciais num endpoint protegido e, se aceitas, abre a sessao. */
  login(username: string, password: string): Observable<void> {
    const token = encodeBasic(username, password);
    const headers = new HttpHeaders({ Authorization: `Basic ${token}` });

    return this.http.get('/api/backup/active', { headers }).pipe(
      tap(() => {
        const stored = { username, token };
        this.credentials.set(stored);
        writeStored(stored);
      }),
      map(() => undefined)
    );
  }

  logout(redirect = true): void {
    this.credentials.set(null);
    writeStored(null);
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }
}

function encodeBasic(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  let binary = '';
  bytes.forEach(b => (binary += String.fromCharCode(b)));
  return btoa(binary);
}

function readStored(): StoredCredentials | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredCredentials) : null;
  } catch {
    return null;
  }
}

function writeStored(value: StoredCredentials | null): void {
  try {
    if (value) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Armazenamento indisponivel (modo privado): a sessao vale so enquanto a aba estiver aberta.
  }
}
