import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideMarkdown } from 'ngx-markdown';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideMarkdown(),
    // Trigger session restoration after AuthService construction finishes so HttpClient
    // interceptors can safely inject AuthService without hitting a circular DI error,
    // without blocking initial UI rendering if the backend is waking up.
    provideAppInitializer(() => {
      inject(AuthService).restoreSession().subscribe();
    }),
  ],
};
