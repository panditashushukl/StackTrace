import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  if (authService.isInitialized()) {
    return router.createUrlTree(['/login']);
  }

  return authService
    .restoreSession()
    .pipe(map((ok) => (ok ? true : router.createUrlTree(['/login']))));
};
