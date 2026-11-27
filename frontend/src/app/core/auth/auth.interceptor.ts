import { HttpContextToken, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { catchError, switchMap, tap, throwError } from 'rxjs';

const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const sessionId = authService.sessionId();
  const isOAuthRedirect = req.url.includes('/oauth2/');

  let authReq = req;
  if (!isOAuthRedirect) {
    authReq = req.clone({
      withCredentials: true,
      ...(sessionId ? { setHeaders: { 'X-Session-Id': sessionId } } : {}),
    });
  }

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
      const isAuthEndpoint =
        req.url.includes('/auth/login') ||
        req.url.includes('/auth/register') ||
        req.url.includes('/auth/refresh') ||
        req.url.includes('/auth/logout');

      if (
        error.status === 401 &&
        !isAuthEndpoint &&
        !req.context.get(RETRIED_AFTER_REFRESH)
      ) {
        return authService.refreshSession().pipe(
          switchMap(() => {
            const retriedReq = authReq.clone({
              context: authReq.context.set(RETRIED_AFTER_REFRESH, true),
            });
            return next(retriedReq);
          }),
          catchError((refreshError) => {
            authService.clearAuth();
            return throwError(() => refreshError);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
