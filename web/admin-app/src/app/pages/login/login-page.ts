import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { describeError } from '../../core/problem';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  template: `
    <div class="flex min-h-screen items-center justify-center px-4">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex flex-col items-center gap-3 text-center">
          <img src="favicon.svg" alt="" class="size-10" />
          <div>
            <h1 class="text-xl font-semibold tracking-tight">ClaimFlow Admin</h1>
            <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">Manage plans and member enrollment.</p>
          </div>
        </div>
        <form class="card space-y-4 p-6" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label for="username" class="label">Username</label>
            <input id="username" class="input" autocomplete="username" formControlName="username" />
          </div>
          <div>
            <label for="password" class="label">Password</label>
            <input id="password" type="password" class="input" autocomplete="current-password" formControlName="password" />
          </div>
          @if (error()) {
            <p class="rounded-lg bg-rose-50 p-3 text-sm text-rose-800 dark:bg-rose-950/40 dark:text-rose-200" role="alert">{{ error() }}</p>
          }
          <button type="submit" class="btn-primary w-full" [disabled]="busy() || form.invalid">{{ busy() ? 'Signing in...' : 'Sign in' }}</button>
          <p class="text-center text-xs text-slate-500 dark:text-slate-400">Demo account: admin / admin-demo</p>
        </form>
      </div>
    </div>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(username, password).subscribe({
      next: () => void this.router.navigate(['/plans']),
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(describeError(e));
      },
    });
  }
}
