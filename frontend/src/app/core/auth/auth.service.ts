import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthResponse, LoginPayload, RegisterPayload, User } from './user.model';
import { environment } from '../../../environments/environment';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sessionKey = 'stb_session_id';
  private readonly legacyTokenKey = 'stb_auth_token';
  private readonly legacyHintKey = 'stb_has_session';

  private readonly sessionSignal = signal<string | null>(this.getOrCreateSessionId());
  private readonly userSignal = signal<User | null>(null);
  private readonly loadingSignal = signal<boolean>(false);
  private readonly initializedSignal = signal<boolean>(false);

  private refreshInFlight$: Observable<User> | null = null;
  private restoreInFlight$: Observable<boolean> | null = null;

  readonly sessionId = this.sessionSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.userSignal());
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly isInitialized = this.initializedSignal.asReadonly();

  constructor() {
    this.cleanupLegacyStorage();
  }

  loginWithGoogle(): void {
    window.location.href = environment.googleAuthUrl;
  }

  loginWithCredentials(payload: LoginPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, payload).pipe(
      tap((res) => {
        this.persistUser(res.user);
        this.router.navigate(['/analyses']);
      })
    );
  }

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/register`, payload).pipe(
      tap((res) => {
        this.persistUser(res.user);
        this.router.navigate(['/analyses']);
      })
    );
  }

  refreshSession(): Observable<User> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http
        .post<AuthResponse>(`${environment.apiUrl}/auth/refresh`, {})
        .pipe(
          map((res) => res.user),
          tap((user) => this.persistUser(user)),
          shareReplay(1),
          finalize(() => {
            this.refreshInFlight$ = null;
          })
        );
    }
    return this.refreshInFlight$;
  }

  updatePassword(newPassword: string): Observable<User> {
    return this.http
      .put<{ message: string }>(`${environment.apiUrl}/users/me/password`, { newPassword })
      .pipe(switchMap(() => this.fetchCurrentUser()));
  }

  handleAuthCallback(): Observable<User> {
    return this.fetchCurrentUser().pipe(
      tap({
        next: () => this.router.navigate(['/analyses']),
        error: () => {
          this.clearAuth();
          this.router.navigate(['/login'], {
            queryParams: { error: 'Unable to verify Google session. Please try again.' },
          });
        },
      })
    );
  }

  fetchCurrentUser(): Observable<User> {
    this.loadingSignal.set(true);
    return this.http.get<User>(`${environment.apiUrl}/users/me`).pipe(
      tap({
        next: (user) => {
          this.persistUser(user);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      })
    );
  }

  restoreSession(): Observable<boolean> {
    if (this.userSignal()) {
      this.loadingSignal.set(false);
      this.initializedSignal.set(true);
      return of(true);
    }
    if (!this.restoreInFlight$) {
      this.restoreInFlight$ = this.fetchCurrentUser().pipe(
        map(() => true),
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
            this.userSignal.set(null);
          }
          return of(false);
        }),
        finalize(() => {
          this.loadingSignal.set(false);
          this.initializedSignal.set(true);
          this.restoreInFlight$ = null;
        }),
        shareReplay(1)
      );
    }
    return this.restoreInFlight$;
  }

  syncSessionId(sessionId: string): void {
    if (!sessionId || sessionId === this.sessionSignal()) {
      return;
    }
    this.sessionSignal.set(sessionId);
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.setItem(this.sessionKey, sessionId);
    }
  }

  clearAuth(): void {
    this.userSignal.set(null);
    this.initializedSignal.set(true);
    this.loadingSignal.set(false);
  }

  logout(): void {
    this.clearAuth();
    this.http.post<void>(`${environment.apiUrl}/auth/logout`, {}).subscribe({
      complete: () => this.router.navigate(['/analyses']),
      error: () => this.router.navigate(['/analyses']),
    });
  }

  private persistUser(user: User): void {
    this.userSignal.set(user);
    this.initializedSignal.set(true);
    this.loadingSignal.set(false);
  }

  private cleanupLegacyStorage(): void {
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.removeItem(this.legacyTokenKey);
      localStorage.removeItem(this.legacyHintKey);
    }
  }

  private getOrCreateSessionId(): string | null {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }
    let existing = localStorage.getItem(this.sessionKey);
    if (!existing && typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      existing = crypto.randomUUID();
      localStorage.setItem(this.sessionKey, existing);
    }
    return existing;
  }
}
