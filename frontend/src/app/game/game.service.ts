import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FactionSummary, MatchView } from './match';

/** Game API calls. A 401 on any of them is handled by authInterceptor. */
@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);

  factions(): Observable<FactionSummary[]> {
    return this.http.get<FactionSummary[]>('/api/factions');
  }

  startMatch(factionId: string): Observable<MatchView> {
    return this.http.post<MatchView>('/api/matches', { factionId });
  }

  currentMatch(): Observable<MatchView> {
    return this.http.get<MatchView>('/api/matches/current');
  }
}
