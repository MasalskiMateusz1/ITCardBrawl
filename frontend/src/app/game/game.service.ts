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

  /** `turn` lets the backend reject a move made against a stale view (409). */
  playCard(matchId: string, turn: number, instanceId: number): Observable<MatchView> {
    return this.http.post<MatchView>(`/api/matches/${matchId}/play`, { turn, instanceId });
  }

  /** `targetId === null` attacks the opponent's stress. */
  attack(
    matchId: string,
    turn: number,
    attackerId: number,
    targetId: number | null,
  ): Observable<MatchView> {
    return this.http.post<MatchView>(`/api/matches/${matchId}/attack`, {
      turn,
      attackerId,
      targetId,
    });
  }

  endTurn(matchId: string, turn: number): Observable<MatchView> {
    return this.http.post<MatchView>(`/api/matches/${matchId}/end-turn`, { turn });
  }
}
