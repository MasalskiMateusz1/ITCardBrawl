import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FactionSummary, MatchView, Target } from './match';

/** Game API calls. A 401 on any of them is handled by authInterceptor. */
@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);
  /** The start response, handed from new-match to the table once; not kept on the server. */
  private startedMatch: MatchView | null = null;

  /** Stores the start response so the table can play back the computer's first turn. */
  setStartedMatch(match: MatchView): void {
    this.startedMatch = match;
  }

  /** Returns the stored start response once, then forgets it. */
  takeStartedMatch(): MatchView | null {
    const match = this.startedMatch;
    this.startedMatch = null;
    return match;
  }

  factions(): Observable<FactionSummary[]> {
    return this.http.get<FactionSummary[]>('/api/factions');
  }

  startMatch(factionId: string): Observable<MatchView> {
    return this.http.post<MatchView>('/api/matches', { factionId });
  }

  currentMatch(): Observable<MatchView> {
    return this.http.get<MatchView>('/api/matches/current');
  }

  /**
   * `turn` lets the backend reject a move made against a stale view (409); `target` is `null` for
   * a card that takes no target.
   */
  playCard(
    matchId: string,
    turn: number,
    instanceId: number,
    target: Target | null,
  ): Observable<MatchView> {
    return this.http.post<MatchView>(`/api/matches/${matchId}/play`, {
      turn,
      instanceId,
      target,
    });
  }

  /** `targetId === null` attacks the opponent player. */
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
