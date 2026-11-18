import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

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
    return this.http.get<Player>('/api/me').pipe(tap((player) => this.currentUser.set(player)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/logout', null).pipe(tap(() => this.currentUser.set(null)));
  }
}
