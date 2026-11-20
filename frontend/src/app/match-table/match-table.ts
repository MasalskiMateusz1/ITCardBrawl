import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subscription, finalize } from 'rxjs';
import { GameService } from '../game/game.service';
import { BoardCharacter, HandCard, MatchView, ReplayStep } from '../game/match';
import { LanguageService } from '../i18n/language.service';

type MatchTableError = 'loadMatch' | 'moveRejected' | 'moveFailed';

/** Mirrors MatchRules.BOARD_LIMIT; only used to disable the button — the backend validates anyway. */
const BOARD_LIMIT = 5;

/** Delay between computer moves played back on the table; specs provide their own. */
export const REPLAY_STEP_MS = new InjectionToken<number>('REPLAY_STEP_MS', {
  providedIn: 'root',
  factory: () => 700,
});

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
  private readonly replayStepMs = inject(REPLAY_STEP_MS);

  protected readonly texts = this.languageService.texts;
  protected readonly match = signal<MatchView | null>(null);
  /** True while a move request is in flight — every move button is disabled then. */
  protected readonly pending = signal(false);
  /** True while the computer's moves are played back — every move button is disabled then. */
  protected readonly replaying = signal(false);
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

  /** The GET /current in flight; a newer one cancels it, so a stale response cannot win. */
  private loadRequest: Subscription | null = null;
  private replayTimer: ReturnType<typeof setTimeout> | null = null;
  /** A language change during playback refetches the table once playback ends. */
  private reloadAfterReplay = false;

  constructor() {
    // The first run is the entry (the start response handed over by new-match, or GET /current);
    // each later run follows a language change.
    let entry = true;
    effect(() => {
      this.languageService.language();
      untracked(() => {
        const started = entry ? this.gameService.takeStartedMatch() : null;
        entry = false;
        if (started !== null) {
          this.show(started);
        } else if (this.replaying()) {
          this.reloadAfterReplay = true;
        } else {
          this.loadMatch();
        }
      });
    });
    inject(DestroyRef).onDestroy(() => {
      this.stopReplay();
      this.loadRequest?.unsubscribe();
    });
  }

  protected canPlay(card: HandCard): boolean {
    const match = this.match();
    if (match === null || !this.myTurn() || this.busy()) {
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

  /** A move request is in flight or the computer's moves are being played back. */
  protected busy(): boolean {
    return this.pending() || this.replaying();
  }

  private move(request: Observable<MatchView>): void {
    // Guards a second click that lands before the disabled state is rendered.
    if (this.busy()) {
      return;
    }
    this.error.set(null);
    this.pending.set(true);
    request.pipe(finalize(() => this.pending.set(false))).subscribe({
      next: (match) => {
        this.selectedAttacker.set(null);
        this.show(match);
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

  /**
   * Shows a response: an empty `replay` sets the state at once; otherwise step 1 is shown now,
   * each next one after REPLAY_STEP_MS, and the final state replaces the last step.
   */
  private show(match: MatchView): void {
    this.stopReplay();
    const steps = match.replay;
    if (steps.length <= 1) {
      this.match.set(match);
      return;
    }
    // A GET /current still in flight would overwrite the playback — redo it once playback ends.
    if (this.loadRequest !== null && !this.loadRequest.closed) {
      this.loadRequest.unsubscribe();
      this.reloadAfterReplay = true;
    }
    this.replaying.set(true);
    const playStep = (index: number): void => {
      if (index === steps.length - 1) {
        this.replayTimer = null;
        this.finishReplay(match);
        return;
      }
      this.match.set(stepView(match, steps[index]));
      this.replayTimer = setTimeout(() => playStep(index + 1), this.replayStepMs);
    };
    playStep(0);
  }

  private finishReplay(match: MatchView): void {
    this.match.set(match);
    this.replaying.set(false);
    if (this.reloadAfterReplay) {
      this.reloadAfterReplay = false;
      this.loadMatch();
    }
  }

  private stopReplay(): void {
    if (this.replayTimer !== null) {
      clearTimeout(this.replayTimer);
      this.replayTimer = null;
    }
    this.replaying.set(false);
  }

  private loadMatch(): void {
    this.loadRequest?.unsubscribe();
    this.loadRequest = this.gameService.currentMatch().subscribe({
      next: (match) => {
        this.show(match);
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

/** The final state with the step's table and the part of the log visible at that step. */
function stepView(final: MatchView, step: ReplayStep): MatchView {
  return {
    ...final,
    turn: step.turn,
    status: step.status,
    result: step.result,
    player: step.player,
    opponent: step.opponent,
    log: final.log.slice(0, step.logSize),
    replay: [],
  };
}

function isGuard(character: BoardCharacter): boolean {
  return character.ability?.keyword === 'GUARD';
}
