import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly mode = signal<'login' | 'register'>('login');
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(
    this.route.snapshot.queryParamMap.get('error')
  );

  identifier = '';
  password = '';

  regName = '';
  regUsername = '';
  regEmail = '';
  regPassword = '';

  switchMode(nextMode: 'login' | 'register'): void {
    this.mode.set(nextMode);
    this.errorMessage.set(null);
  }

  submitLogin(): void {
    if (!this.identifier.trim() || !this.password) {
      this.errorMessage.set('Enter your username or email and password.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService
      .loginWithCredentials({
        identifier: this.identifier.trim(),
        password: this.password,
      })
      .subscribe({
        next: () => this.submitting.set(false),
        error: (err) => {
          this.submitting.set(false);
          this.errorMessage.set(
            err?.error?.message || 'Sign-in failed. Check your credentials.'
          );
        },
      });
  }

  submitRegister(): void {
    if (!this.regName.trim() || !this.regUsername.trim() || !this.regEmail.trim() || this.regPassword.length < 8) {
      this.errorMessage.set('Fill in all fields (password must be at least 8 characters).');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService
      .register({
        name: this.regName.trim(),
        username: this.regUsername.trim(),
        email: this.regEmail.trim(),
        password: this.regPassword,
      })
      .subscribe({
        next: () => this.submitting.set(false),
        error: (err) => {
          this.submitting.set(false);
          this.errorMessage.set(
            err?.error?.message || 'Registration failed. Try another email or username.'
          );
        },
      });
  }

  signInWithGoogle(): void {
    this.authService.loginWithGoogle();
  }
}
