import { HttpErrorResponse } from '@angular/common/http';

/** Envelope de listas devolvido pelo backend (CollectionResponse). */
export interface CollectionResponse<T> {
  success: boolean;
  count: number;
  items: T[];
  message?: string;
  requestId?: string;
  timestamp?: string;
}

/** Envelope de respostas com payload (MutationResponse). */
export interface MutationResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  details?: string;
  requestId?: string;
  timestamp?: string;
}

/** Resposta de operacoes de controle (OperationResponse). */
export interface OperationResponse {
  success: boolean;
  message?: string;
  error?: string;
  status?: string;
  taskId?: number;
  taskIds?: number[];
  requestId?: string;
  timestamp?: string;
}

/** Corpo padrao de erro do backend (ApiErrorResponse). */
export interface ApiErrorResponse {
  success: false;
  status: number;
  error: string;
  code?: string;
  details?: unknown;
  path?: string;
  taskId?: number;
  requestId?: string;
  timestamp?: string;
}

/** Extrai uma mensagem legivel de um erro HTTP, priorizando o corpo ApiErrorResponse. */
export function apiErrorMessage(err: unknown, fallback = 'Erro inesperado ao comunicar com o servidor.'): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiErrorResponse> | string | null;
    if (body && typeof body === 'object' && typeof body.error === 'string') {
      return body.error;
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    // Status 0 (rede) ou 5xx sem corpo vem do proxy/gateway: o backend nunca respondeu.
    if (err.status === 0 || (err.status >= 500 && !body)) {
      return 'Servidor indisponivel. Verifique se o backend esta em execucao.';
    }
  }
  return fallback;
}
