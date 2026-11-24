import { Routes } from '@angular/router';
import { adminGuard } from './core/auth.guard';
import { LoginPage } from './pages/login/login-page';
import { Shell } from './shell';

export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'Sign in - ClaimFlow Admin' },
  {
    path: '',
    component: Shell,
    canActivate: [adminGuard],
    children: [
      { path: 'plans', loadComponent: () => import('./pages/plans/plans-page').then((m) => m.PlansPage), title: 'Plans - ClaimFlow Admin' },
      { path: 'members', loadComponent: () => import('./pages/members/members-page').then((m) => m.MembersPage), title: 'Members - ClaimFlow Admin' },
      { path: '', pathMatch: 'full', redirectTo: 'plans' },
    ],
  },
  { path: '**', redirectTo: 'plans' },
];
