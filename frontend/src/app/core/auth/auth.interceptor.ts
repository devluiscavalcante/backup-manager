import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Anexa as credenciais Basic nas chamadas a API e encerra a sessao quando o backend responde 401.
 * O cabecalho X-Requested-With faz o backend omitir o desafio Basic, evitando o popup nativo do navegador.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const authorization = req.headers.get('Authorization') ?? auth.authorizationHeader();
  const isLoginAttempt = req.headers.has('Authorization');

  const request = req.clone({
    setHeaders: {
      'X-Requested-With': 'XMLHttpRequest',
      ...(authorization ? { Authorization: authorization } : {})
    }
  });

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !isLoginAttempt) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
