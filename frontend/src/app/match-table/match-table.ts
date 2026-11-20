import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subscription, finalize } from 'rxjs';
import { GameService } from '../game/game.service';
import { BoardCharacter, HandCard, MatchView } from '../game/match';
import { LanguageService } from '../i18n/language.service';

type MatchTableError = 'loadMatch' | 'moveRejected' | 'moveFailed';

/** Mirrors MatchRules.BOARD_LIMIT; only used to disable the button — the backend validates anyway. */
const BOARD_LIMIT = 5;

@Component({
  imports: [RouterLink],
  selector: 'app-match-table',
  styleUrl: './match-table.css',
  templateUrl: './match-table.html',
})
export class MatchTable {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);
  private readonly languageService = inject(LanguageService);

  protected readonly texts = this.languageService.texts;
  protected readonly match = signal<MatchView | null>(null);
  /** True while a move request is in flight — every move button is disabled then. */
  protected readonly pending = signal(false);
  /** The player's character chosen to attack; targets are offered only while one is selected. */
  protected readonly selectedAttacker = signal<number | null>(null);
  /** A key, not text — the message is taken from the dictionary at render time. */
  protected readonly error = signal<MatchTableError | null>(null);
  protected readonly errorText = computed(() => {
    switch (this.error()) {
      case 'loadMatch':
        return this.texts().matchTable.loadFailed;
      case 'moveRejected':
        return this.texts().matchTable.moveRejected;
      case 'moveFailed':
        return this.texts().matchTable.moveFailed;
      default:
        return null;
    }
  });

  protected readonly finished = computed(() => this.match()?.status === 'FINISHED');
  /** The player may act: the match is running and it is the player's turn. */
  protected readonly myTurn = computed(() => {
    const match = this.match();
    return match !== null && match.status === 'IN_PROGRESS' && match.turn.active === 'PLAYER';
  });
  /** An opponent character with Guard must be attacked first — no stress, no other targets. */
  protected readonly opponentGuarded = computed(
    () => this.match()?.opponent.board.some(isGuard) ?? false,
  );

  constructor() {
    // The first run is the entry request; each later run follows a language change.
    // Cleanup cancels a request still in flight, so a stale response cannot overwrite a newer one.
    effect((onCleanup) => {
      this.languageService.language();
      const request = untracked(() => this.loadMatch());
      onCleanup(() => request.unsubscribe());
    });
  }

  protected canPlay(card: HandCard): boolean {
    const match = this.match();
    if (match === null || !this.myTurn() || this.pending()) {
      return false;
    }
    if (card.cost > match.player.resource.current) {
      return false;
    }
    return card.type !== 'CHARACTER' || match.player.board.length < BOARD_LIMIT;
  }

  protected canTarget(character: BoardCharacter): boolean {
    return !this.opponentGuarded() || isGuard(character);
  }

  protected play(card: HandCard): void {
    const match = this.match();
    if (match === null) {
      return;
    }
    this.move(this.gameService.playCard(match.matchId, match.turn.number, card.instanceId));
  }

  protected selectAttacker(character: BoardCharacter): void {
    this.selectedAttacker.set(character.instanceId);
  }

  protected cancelAttack(): void {
    this.selectedAttacker.set(null);
  }

  /** `targetId === null` attacks the opponent's stress. */
  protected attack(targetId: number | null): void {
    const match = this.match();
    const attackerId = this.selectedAttacker();
    if (match === null || attackerId === null) {
      return;
    }
    this.move(this.gameService.attack(match.matchId, match.turn.number, attackerId, targetId));
  }

  protected endTurn(): void {
    const match = this.match();
    if (match === null) {
      return;
    }
    this.move(this.gameService.endTurn(match.matchId, match.turn.number));
  }

  private move(request: Observable<MatchView>): void {
    this.error.set(null);
    this.pending.set(true);
    request.pipe(finalize(() => this.pending.set(false))).subscribe({
      next: (match) => {
        this.match.set(match);
        this.selectedAttacker.set(null);
      },
      error: (error: unknown) => {
        this.selectedAttacker.set(null);
        if (!(error instanceof HttpErrorResponse)) {
          this.error.set('moveFailed');
        } else if (error.status === 409) {
          // The view was stale (another turn or match) — refresh it silently.
          this.loadMatch();
        } else if (error.status === 400) {
          this.error.set('moveRejected');
        } else if (error.status !== 401) {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          this.error.set('moveFailed');
        }
      },
    });
  }

  private loadMatch(): Subscription {
    return this.gameService.currentMatch().subscribe({
      next: (match) => {
        this.match.set(match);
        this.error.set(null);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          // No match in progress (e.g. after a backend restart) — pick a faction again.
          void this.router.navigateByUrl('/new-match');
        } else if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          // A 401 is handled by authInterceptor, which redirects to /sign-in.
          // Drop data from an earlier load so it isn't shown in the old language next to the error.
          this.match.set(null);
          this.error.set('loadMatch');
        }
      },
    });
  }
}

function isGuard(character: BoardCharacter): boolean {
  return character.ability?.keyword === 'GUARD';
}
