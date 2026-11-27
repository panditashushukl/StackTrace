import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom, isObservable, of } from 'rxjs';
import { vi } from 'vitest';

describe('authGuard', () => {
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    authService = TestBed.inject(AuthService);
    TestBed.inject(Router);
  });

  it('should redirect to /login when unauthenticated after session check', async () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(false);
    vi.spyOn(authService, 'restoreSession').mockReturnValue(of(false));
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    const resolved = isObservable(result) ? await firstValueFrom(result) : result;
    expect(resolved instanceof UrlTree).toBe(true);
    expect(resolved.toString()).toContain('/login');
  });

  it('should allow access when session is restored from cookies', async () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(false);
    vi.spyOn(authService, 'restoreSession').mockReturnValue(of(true));
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    const resolved = isObservable(result) ? await firstValueFrom(result) : result;
    expect(resolved).toBe(true);
  });

  it('should allow access immediately when already authenticated', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(result).toBe(true);
  });
});
