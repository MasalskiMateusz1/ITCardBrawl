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
import {
  AbilityKeyword,
  BoardCharacter,
  HandCard,
  MatchView,
  ReplayStep,
  Side,
  Target,
} from '../game/match';
import { LanguageService } from '../i18n/language.service';

type MatchTableError = 'loadMatch' | 'moveRejected' | 'moveFailed';

/** Delay between computer moves played back on the table; specs provide their own. */
export const REPLAY_STEP_MS = new InjectionToken<number>('REPLAY_STEP_MS', {
  providedIn: 'root',
  factory: () => 700,
});

/** The countdown is highlighted from this many seconds left. */
const URGENT_SECONDS = 10;

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
  /**
   * The event card waiting for its target; event targets are offered only while one is selected.
   * Never set together with `selectedAttacker`.
   */
  protected readonly selectedEvent = signal<number | null>(null);
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
  /** The reason of the playback step currently shown, as sent by the backend. */
  private readonly replayReason = signal<string | null>(null);
  /**
   * The computer's reason for the move shown in the current playback step. Taken from the step,
   * not from the log: the first step's log still ends with the previous turn's reasons.
   */
  protected readonly moveReason = computed(() => (this.replaying() ? this.replayReason() : null));
  /**
   * Seconds left in the player's turn, counted from `turn.timeLimitSeconds` once playback ends;
   * null when no countdown runs.
   */
  protected readonly secondsLeft = signal<number | null>(null);
  protected readonly urgent = computed(() => {
    const seconds = this.secondsLeft();
    return seconds !== null && seconds <= URGENT_SECONDS;
  });
  /** The legal attack targets of the selected attacker, as sent by the backend. */
  private readonly attackTargets = computed(() => {
    const attackerId = this.selectedAttacker();
    return this.match()?.player.board.find((c) => c.instanceId === attackerId)?.attackTargets ?? [];
  });
  /** The legal targets of the selected event card, as sent by the backend. */
  private readonly eventTargets = computed(() => {
    const eventId = this.selectedEvent();
    return this.match()?.player.hand.find((c) => c.instanceId === eventId)?.targets ?? [];
  });

  /** The GET /current in flight; a newer one cancels it, so a stale response cannot win. */
  private loadRequest: Subscription | null = null;
  private replayTimer: ReturnType<typeof setTimeout> | null = null;
  /** A language change during playback refetches the table once playback ends. */
  private reloadAfterReplay = false;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  /** The match and turn the countdown runs for; a response for the same turn keeps it running. */
  private countdownTurn: string | null = null;

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
      this.stopCountdown();
      this.loadRequest?.unsubscribe();
    });
  }

  /** Legality comes from the backend; the SPA does not repeat the rules. */
  protected canPlay(card: HandCard): boolean {
    return !this.busy() && card.playable;
  }

  /** A card that needs a target enters target mode; any other card is played at once. */
  protected play(card: HandCard): void {
    if (card.requiresTarget) {
      this.selectedAttacker.set(null);
      this.selectedEvent.set(card.instanceId);
      return;
    }
    this.sendPlay(card.instanceId, null);
  }

  /** `instanceId === null` targets the player of `side`. */
  protected canTargetEvent(instanceId: number | null, side: Side): boolean {
    return this.eventTargets().some((target) => sameTarget(target, instanceId, side));
  }

  protected chooseTarget(instanceId: number | null, side: Side): void {
    const eventId = this.selectedEvent();
    if (eventId === null) {
      return;
    }
    const target: Target =
      instanceId === null ? { kind: 'PLAYER', side } : { kind: 'CHARACTER', instanceId };
    this.sendPlay(eventId, target);
  }

  protected cancelTarget(): void {
    this.selectedEvent.set(null);
  }

  /** `targetId === null` is the opponent player. */
  protected canAttackTarget(targetId: number | null): boolean {
    return this.attackTargets().some((target) => sameTarget(target, targetId, 'OPPONENT'));
  }

  /** Keywords given by active events — the character's own ability is already shown as text. */
  protected grantedKeywords(character: BoardCharacter): AbilityKeyword[] {
    return character.keywords.filter((keyword) => keyword !== character.ability?.keyword);
  }

  protected selectAttacker(character: BoardCharacter): void {
    this.selectedEvent.set(null);
    this.selectedAttacker.set(character.instanceId);
  }

  protected cancelAttack(): void {
    this.selectedAttacker.set(null);
  }

  /** `targetId === null` attacks the opponent player. */
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

  private sendPlay(instanceId: number, target: Target | null): void {
    const match = this.match();
    if (match === null) {
      return;
    }
    this.move(this.gameService.playCard(match.matchId, match.turn.number, instanceId, target));
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
        this.clearSelection();
        this.show(match);
      },
      error: (error: unknown) => {
        this.clearSelection();
        if (!(error instanceof HttpErrorResponse)) {
          this.error.set('moveFailed');
        } else if (error.status === 409 || error.status === 404) {
          // 409: the view was stale (another turn or match) — refresh it silently.
          // 404: the match is gone (e.g. backend restart) — loadMatch sends the player to /new-match.
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

  private clearSelection(): void {
    this.selectedAttacker.set(null);
    this.selectedEvent.set(null);
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
      this.updateCountdown(match);
      return;
    }
    // The turn changes with the playback — the countdown starts again once it ends.
    this.stopCountdown();
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
      this.replayReason.set(steps[index].reason);
      this.replayTimer = setTimeout(() => playStep(index + 1), this.replayStepMs);
    };
    playStep(0);
  }

  private finishReplay(match: MatchView): void {
    this.match.set(match);
    this.updateCountdown(match);
    this.replaying.set(false);
    this.replayReason.set(null);
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
    this.replayReason.set(null);
  }

  /**
   * Starts the countdown when the player's turn begins, keeps it for another response in the same
   * turn and stops it outside the player's turn. At zero the turn is ended, unless a request is in
   * flight.
   */
  private updateCountdown(match: MatchView): void {
    const limit = match.turn.timeLimitSeconds;
    if (match.status !== 'IN_PROGRESS' || match.turn.active !== 'PLAYER' || limit === null) {
      this.stopCountdown();
      return;
    }
    const turn = `${match.matchId}:${match.turn.number}`;
    if (turn === this.countdownTurn) {
      return;
    }
    this.stopCountdown();
    this.countdownTurn = turn;
    this.secondsLeft.set(limit);
    this.countdownTimer = setInterval(() => {
      const left = Math.max(0, (this.secondsLeft() ?? 0) - 1);
      this.secondsLeft.set(left);
      if (left === 0) {
        this.clearCountdownTimer();
        if (!this.busy()) {
          this.endTurn();
        }
      }
    }, 1000);
  }

  private stopCountdown(): void {
    this.clearCountdownTimer();
    this.countdownTurn = null;
    this.secondsLeft.set(null);
  }

  private clearCountdownTimer(): void {
    if (this.countdownTimer !== null) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
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
          this.stopCountdown();
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

/** `instanceId === null` means the player of `side`; a character target ignores `side`. */
function sameTarget(target: Target, instanceId: number | null, side: Side): boolean {
  return instanceId === null
    ? target.kind === 'PLAYER' && target.side === side
    : target.kind === 'CHARACTER' && target.instanceId === instanceId;
}
