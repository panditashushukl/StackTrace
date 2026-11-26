import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-950 text-slate-100">
      <header class="bg-slate-900/80 backdrop-blur border-b border-slate-800">
        <div class="max-w-3xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between">
          <a routerLink="/analyses" class="flex items-center gap-2 text-sm text-slate-300 hover:text-white transition">
            <span>&larr;</span>
            <span>Back to Workbench</span>
          </a>
          <button
            type="button"
            (click)="authService.logout()"
            class="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition cursor-pointer"
          >
            Sign out
          </button>
        </div>
      </header>

      <main class="max-w-3xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        @if (authService.user(); as user) {
          <section class="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 flex flex-col sm:flex-row sm:items-center gap-4 justify-between">
            <div class="flex items-center gap-4">
              @if (user.avatarUrl) {
                <img [src]="user.avatarUrl" [alt]="user.name" class="w-14 h-14 rounded-full ring-2 ring-indigo-500/40 object-cover" />
              } @else {
                <div class="w-14 h-14 rounded-full bg-indigo-600 flex items-center justify-center font-bold text-lg text-white">
                  {{ user.name.charAt(0).toUpperCase() }}
                </div>
              }
              <div>
                <h1 class="text-lg font-semibold text-white">{{ user.name }}</h1>
                <p class="text-xs text-slate-400">&#64;{{ user.username }} &middot; {{ user.email }}</p>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              @if (user.googleLinked) {
                <span class="px-2.5 py-1 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
                  Google Linked
                </span>
              }
              @if (user.hasPassword) {
                <span class="px-2.5 py-1 rounded-full text-[11px] font-medium bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
                  Password Enabled
                </span>
              }
            </div>
          </section>

          <section class="bg-slate-900/70 border border-slate-800 rounded-2xl p-6">
            <h2 class="text-base font-semibold text-white">
              {{ user.hasPassword ? 'Change Password' : 'Set Password' }}
            </h2>
            <p class="text-xs text-slate-400 mt-1">
              Update your account password directly without re-entering a previous password.
            </p>

            @if (successMessage()) {
              <div class="mt-4 rounded-lg bg-emerald-950/60 border border-emerald-500/40 px-3.5 py-2.5 text-xs text-emerald-300">
                {{ successMessage() }}
              </div>
            }
            @if (errorMessage()) {
              <div class="mt-4 rounded-lg bg-red-950/60 border border-red-500/40 px-3.5 py-2.5 text-xs text-red-300">
                {{ errorMessage() }}
              </div>
            }

            <form (ngSubmit)="submitPasswordUpdate()" class="mt-5 space-y-4 max-w-md">
              <div>
                <label for="newPassword" class="block text-xs font-medium text-slate-300 mb-1.5">
                  New Password
                </label>
                <input
                  id="newPassword"
                  name="newPassword"
                  type="password"
                  [(ngModel)]="newPassword"
                  placeholder="Minimum 8 characters"
                  class="w-full rounded-xl bg-slate-950 border border-slate-800 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition"
                />
              </div>

              <div>
                <label for="confirmPassword" class="block text-xs font-medium text-slate-300 mb-1.5">
                  Confirm Password
                </label>
                <input
                  id="confirmPassword"
                  name="confirmPassword"
                  type="password"
                  [(ngModel)]="confirmPassword"
                  placeholder="Repeat new password"
                  class="w-full rounded-xl bg-slate-950 border border-slate-800 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition"
                />
              </div>

              <button
                type="submit"
                [disabled]="saving()"
                class="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-xs font-semibold text-white transition cursor-pointer"
              >
                {{ saving() ? 'Saving...' : 'Update Password' }}
              </button>
            </form>
          </section>
        } @else {
          <div class="bg-slate-900/70 border border-slate-800 rounded-2xl p-8 text-center text-sm text-slate-400">
            Loading profile...
          </div>
        }
      </main>
    </div>
  `,
})
export class ProfileComponent {
  readonly authService = inject(AuthService);

  newPassword = '';
  confirmPassword = '';

  readonly saving = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  submitPasswordUpdate(): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);

    if (this.newPassword.length < 8) {
      this.errorMessage.set('Password must be at least 8 characters long.');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    this.saving.set(true);
    this.authService.updatePassword(this.newPassword).subscribe({
      next: () => {
        this.saving.set(false);
        this.newPassword = '';
        this.confirmPassword = '';
        this.successMessage.set('Password updated. You can now sign in with your ID or email and this password.');
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(err?.error?.message || 'Unable to update password.');
      },
    });
  }
}
