import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthResponse, LoginPayload, RegisterPayload, User } from './user.model';
import { environment } from '../../../environments/environment';
import { Observable, switchMap, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenKey = 'stb_auth_token';
  private readonly sessionKey = 'stb_session_id';

  private readonly tokenSignal = signal<string | null>(this.getStoredToken());
  private readonly sessionSignal = signal<string | null>(this.getOrCreateSessionId());
  private readonly userSignal = signal<User | null>(null);
  private readonly loadingSignal = signal<boolean>(false);

  readonly token = this.tokenSignal.asReadonly();
  readonly sessionId = this.sessionSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());
  readonly isLoading = this.loadingSignal.asReadonly();

  constructor() {
    if (this.tokenSignal()) {
      this.fetchCurrentUser().subscribe({
        error: () => this.clearAuth(),
      });
    }
  }

  loginWithGoogle(): void {
    window.location.href = environment.googleAuthUrl;
  }

  loginWithCredentials(payload: LoginPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, payload).pipe(
      tap((res) => {
        this.persistAuth(res.token, res.user);
        this.router.navigate(['/analyses']);
      })
    );
  }

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/register`, payload).pipe(
      tap((res) => {
        this.persistAuth(res.token, res.user);
        this.router.navigate(['/analyses']);
      })
    );
  }

  updatePassword(newPassword: string): Observable<User> {
    return this.http
      .put<{ message: string }>(`${environment.apiUrl}/users/me/password`, { newPassword })
      .pipe(switchMap(() => this.fetchCurrentUser()));
  }

  handleAuthCallback(token: string): Observable<User> {
    this.tokenSignal.set(token);
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.setItem(this.tokenKey, token);
    }
    return this.fetchCurrentUser().pipe(
      tap({
        next: () => this.router.navigate(['/analyses']),
        error: () => this.logout(),
      })
    );
  }

  fetchCurrentUser(): Observable<User> {
    this.loadingSignal.set(true);
    return this.http.get<User>(`${environment.apiUrl}/users/me`).pipe(
      tap({
        next: (user) => {
          this.userSignal.set(user);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      })
    );
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
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.removeItem(this.tokenKey);
    }
  }

  logout(): void {
    this.clearAuth();
    this.router.navigate(['/analyses']);
  }

  private persistAuth(token: string, user: User): void {
    this.tokenSignal.set(token);
    this.userSignal.set(user);
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.setItem(this.tokenKey, token);
    }
  }

  private getStoredToken(): string | null {
    if (typeof window !== 'undefined' && window.localStorage) {
      return localStorage.getItem(this.tokenKey);
    }
    return null;
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
