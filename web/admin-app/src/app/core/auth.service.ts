import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from './models';

interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: User;
}

const TOKEN_KEY = 'claimflow.admin.token';
const USER_KEY = 'claimflow.admin.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<User | null>(this.restore());

  readonly user = this.currentUser.asReadonly();
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  get token(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  login(username: string, password: string): Observable<User> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, { username, password }).pipe(
      map((response) => {
        if (response.user.role !== 'ADMIN') {
          throw new Error('This console is for admins. Use the claims portal for member and adjuster work.');
        }
        sessionStorage.setItem(TOKEN_KEY, response.accessToken);
        sessionStorage.setItem(USER_KEY, JSON.stringify(response.user));
        this.currentUser.set(response.user);
        return response.user;
      }),
    );
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
  }

  private restore(): User | null {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw && sessionStorage.getItem(TOKEN_KEY) ? (JSON.parse(raw) as User) : null;
  }
}
