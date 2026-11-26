import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center justify-center min-h-screen bg-slate-950 text-white">
      <div class="flex flex-col items-center gap-4 bg-slate-900 p-8 rounded-2xl border border-slate-800 shadow-xl max-w-sm text-center">
        <div class="w-12 h-12 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
        <h2 class="text-lg font-semibold">Completing Sign-In</h2>
        <p class="text-sm text-slate-400">Authenticating with Google OAuth and establishing secure session...</p>
      </div>
    </div>
  `,
})
export class AuthCallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (token) {
      this.authService.handleAuthCallback(token).subscribe();
    } else {
      console.error('OAuth callback failed:', error);
      this.router.navigate(['/login'], {
        queryParams: { error: error || 'Authentication process failed. Please try again.' },
      });
    }
  }
}
