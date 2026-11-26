import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { catchError, tap, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.token();
  const sessionId = authService.sessionId();

  const headers: Record<string, string> = {};
  if (token && !req.url.includes('/oauth2/')) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (sessionId && !req.url.includes('/oauth2/')) {
    headers['X-Session-Id'] = sessionId;
  }

  const authReq = Object.keys(headers).length > 0 ? req.clone({ setHeaders: headers }) : req;

  return next(authReq).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        const serverSessionId = event.headers.get('X-Session-Id');
        if (serverSessionId) {
          authService.syncSessionId(serverSessionId);
        }
      }
    }),
    catchError((error) => {
      if (
        error.status === 401 &&
        token &&
        !req.url.includes('/users/me') &&
        !req.url.includes('/auth/')
      ) {
        authService.clearAuth();
      }
      return throwError(() => error);
    })
  );
};
