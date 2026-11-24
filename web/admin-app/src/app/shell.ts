import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-10 border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-950/80">
      <div class="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4 sm:px-6">
        <a routerLink="/plans" class="flex items-center gap-2 font-semibold tracking-tight">
          <img src="favicon.svg" alt="" class="size-6" />
          ClaimFlow <span class="font-normal text-slate-400">Admin</span>
        </a>
        <nav class="flex items-center gap-1">
          @for (link of links; track link.path) {
            <a
              [routerLink]="link.path"
              routerLinkActive="bg-slate-900 text-white dark:bg-white dark:text-slate-900"
              [routerLinkActiveOptions]="{ exact: false }"
              class="rounded-md px-3 py-1.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
              >{{ link.label }}</a
            >
          }
        </nav>
        <div class="ml-auto flex items-center gap-3">
          <span class="hidden text-sm font-medium sm:block">{{ auth.user()?.displayName }}</span>
          <button type="button" class="btn-secondary px-2.5 py-1.5 text-xs" (click)="logout()">Sign out</button>
        </div>
      </div>
    </header>
    <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <router-outlet />
    </main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly links = [
    { path: '/plans', label: 'Plans' },
    { path: '/members', label: 'Members' },
  ];

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
