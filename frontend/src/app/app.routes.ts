import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { LoginComponent } from './features/auth/login/login.component';
import { AuthCallbackComponent } from './features/auth/callback/auth-callback.component';
import { ProfileComponent } from './features/auth/profile/profile.component';
import { DashboardComponent } from './features/analyses/dashboard.component';

export const routes: Routes = [
  {
    path: 'analyses',
    component: DashboardComponent,
  },
  {
    path: 'login',
    component: LoginComponent,
  },
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [authGuard],
  },
  {
    path: 'auth/callback',
    component: AuthCallbackComponent,
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'analyses',
  },
  {
    path: '**',
    redirectTo: 'analyses',
  },
];
