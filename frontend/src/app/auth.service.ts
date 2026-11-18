import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { SKIP_AUTH_REDIRECT } from './auth.interceptor';

export type Player = {
  provider: 'github' | 'google';
  displayName: string;
  avatarUrl: string | null;
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<Player | null>(null);

  readonly user = this.currentUser.asReadonly();

  loadMe(): Observable<Player> {
    // authGuard decides where a failed probe goes, so the interceptor must not redirect.
    const context = new HttpContext().set(SKIP_AUTH_REDIRECT, true);
    return this.http
      .get<Player>('/api/me', { context })
      .pipe(tap((player) => this.currentUser.set(player)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/logout', null).pipe(tap(() => this.currentUser.set(null)));
  }
}
