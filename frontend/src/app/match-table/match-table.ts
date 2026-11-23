import { HttpErrorResponse } from '@angular/common/http';
import {
  AnimationCallbackEvent,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  Injector,
  afterNextRender,
  afterRenderEffect,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subscription, finalize } from 'rxjs';
import { GameService } from '../game/game.service';
import {
  ActiveEvent,
  BoardCharacter,
  CardType,
  HandCard,
  MatchView,
  ReplayStep,
  Side,
  Target,
} from '../game/match';
import { LanguageSwitch } from '../i18n/language-switch';
import { LanguageService } from '../i18n/language.service';
import { Messages } from '../i18n/messages';
import { Card } from '../ui/card/card';
import { AimArrow } from './aim-arrow/aim-arrow';
import { BoardOverlay } from './board-overlay/board-overlay';
import { BoardSkeleton } from './board-skeleton/board-skeleton';
import { BoardSlot } from './board-slot/board-slot';
import {
  ATTACK_FX_MS,
  DamageFloat,
  DamageFloats,
  FxCue,
  FxDirective,
  IMPACT_MS,
  leaveBoard,
} from './attack-fx/attack-fx';
import { DragGhost } from './drag-ghost/drag-ghost';
import { DropZone } from './drop-zone/drop-zone';
import { GraveyardList } from './graveyard-list/graveyard-list';
import { HandSlot } from './hand-slot/hand-slot';
import { HoverAnchor, HoverCard, HoverPlacement } from './hover-card/hover-card';
import { LiftedCard } from './lifted-card/lifted-card';
import { SideCards } from './side-cards/side-cards';
import { StatusPanel } from './status-panel/status-panel';
import { TurnTimer } from './turn-timer/turn-timer';
import { ZoneSide } from './zone-side/zone-side';

type MatchTableError = 'loadMatch' | 'moveRejected' | 'moveFailed';

/** The overlay opened over the main column; the game over shows on its own (see the template). */
type BoardOverlayKind = 'graveyard-player' | 'graveyard-opponent';

/** Delay between computer moves played back on the table; specs provide their own. */
export const REPLAY_STEP_MS = new InjectionToken<number>('REPLAY_STEP_MS', {
  providedIn: 'root',
  factory: () => 700,
});

/** The countdown is highlighted from this many seconds left. */
const URGENT_SECONDS = 10;

/** Board slots per side (`MatchRules.BOARD_LIMIT`); free ones render as dashed empty slots. */
const BOARD_SLOTS = 5;

/** Where a clickable card sits: the player's hand or a side's board. */
type CardZone = 'HAND' | Side;

/** What a click on a card (or a status panel) does in the current state. */
type CardAction = 'play' | 'cancelEvent' | 'select' | 'cancelAttack' | 'attack' | 'target';

/**
 * The hovered or focused card the enlarged hover card shows, and where it sits on screen;
 * resolved against the current view on every render.
 */
type PreviewRef = {
  zone: CardZone | 'PLAYER_EVENT' | 'OPPONENT_EVENT';
  instanceId: number;
  anchor: HoverAnchor;
};

/** The `app-card` inputs of one card, taken from its view object. */
type CardProps = {
  cardId: string;
  faction: string;
  type: CardType;
  name: string;
  cost: number;
  attack: number | null;
  health: number | null;
  baseAttack: number | null;
  baseHealth: number | null;
  damaged: boolean;
  keyword: string | null;
  ability: string | null;
  text: string | null;
  effects: string[];
  duration: number | null;
  turnsLeft: number | null;
  trapped: boolean;
};

/**
 * A card on the table with its props and the action a click performs (`null` = none legal).
 * `guard`: a board character whose effective keywords include Guard (shield plate, named in the label).
 */
type CardItem<T> = {
  item: T;
  props: CardProps;
  action: CardAction | null;
  label: string;
  guard: boolean;
};

/** Pointer travel (screen px) after which a press on a card becomes a drag, not a click. */
const DRAG_THRESHOLD_PX = 6;
/** Ghost tilt per px of horizontal travel between two pointer moves, its limit (degrees) … */
const DRAG_TILT_PER_PX = 0.6;
const DRAG_TILT_MAX = 10;
/** … and how long the pointer rests before the ghost straightens up again. */
const DRAG_TILT_REST_MS = 90;

/** Distance (px) from the newest log entry within which the log keeps following new entries. */
const LOG_STICK_PX = 24;

/** Viewport the board is drawn for at `--ui-scale: 1` (CSS px); smaller ones tighten in CSS. */
const DESIGN_WIDTH = 1600;
const DESIGN_HEIGHT = 900;

/**
 * A card pressed with the pointer: a hand card to play (`HAND`) or an own character to attack with
 * (`PLAYER`). `pressed` is still a click; past the threshold it is `dragging`; Escape (or the card
 * losing its action) makes it `cancelled` until the pointer is released, so the release does not
 * click the card either.
 */
type DragPress = {
  zone: DragZone;
  instanceId: number;
  pointerId: number;
  source: HTMLElement;
  startX: number;
  startY: number;
  lastX: number;
  grab: { x: number; y: number };
  state: 'pressed' | 'dragging' | 'cancelled';
};

type DragZone = 'HAND' | 'PLAYER';

/**
 * What a dragged card is dropped (or aimed) on: a character (`instanceId`) or the player of `side`;
 * `element` is the board item or status panel under the pointer.
 */
type DropTarget = { instanceId: number | null; side: Side; element: Element };

/** A point on screen (px). */
type Point = { x: number; y: number };

/** The board before an attack was sent and where its attacker and target were on screen. */
type AttackSnapshot = {
  before: MatchView;
  attackerId: number;
  targetId: number | null;
  attackerRect: DOMRect | null;
  targetRect: DOMRect | null;
};

/** The aim arrow: from the attacker's card to the pointer, or to the legal target it is over. */
type Aim = { from: Point; to: Point; locked: boolean };

@Component({
  imports: [
    RouterLink,
    NgTemplateOutlet,
    Card,
    AimArrow,
    BoardOverlay,
    BoardSkeleton,
    BoardSlot,
    DamageFloats,
    FxDirective,
    DragGhost,
    DropZone,
    GraveyardList,
    HandSlot,
    HoverCard,
    LiftedCard,
    SideCards,
    StatusPanel,
    TurnTimer,
    ZoneSide,
    LanguageSwitch,
  ],
  selector: 'app-match-table',
  styleUrl: './match-table.css',
  templateUrl: './match-table.html',
  // The hover card is fixed to the viewport: it would drift from its card on scroll or resize.
  host: {
    '(scroll)': 'hidePreview()',
    '(window:resize)': 'onResize()',
    '[style.--ui-scale]': 'uiScale()',
  },
})
export class MatchTable {
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);
  private readonly languageService = inject(LanguageService);
  private readonly replayStepMs = inject(REPLAY_STEP_MS);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly texts = this.languageService.texts;
  /**
   * How much the board grows past its design size (`--ui-scale`, zoom of the board regions):
   * 1 up to 1600×900, then uniformly with the viewport, so a big screen shows the same board larger.
   */
  protected readonly uiScale = signal(boardScale());
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
  /**
   * The graveyard overlay opened from a status panel, `null` when none is. It stacks above the
   * game-over overlay, which renders whenever `result` is set.
   */
  protected readonly openOverlay = signal<BoardOverlayKind | null>(null);
  /** The computer is moving (its turn, or its moves being played back): the player zone dims. */
  protected readonly opponentTurn = computed(() => {
    const match = this.match();
    return (
      this.replaying() ||
      (match !== null && match.status === 'IN_PROGRESS' && match.turn.active === 'OPPONENT')
    );
  });
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
  /**
   * The card dragged with the pointer (past the threshold): a hand card, whose ghost follows
   * `dragPoint` held at `dragGrab`, or an own character aiming an attack. A drag is a shortcut for
   * the card's click (see `onCardPointerDown`).
   */
  private readonly drag = signal<{ zone: DragZone; instanceId: number } | null>(null);
  protected readonly dragPoint = signal({ x: 0, y: 0, tilt: 0 });
  protected readonly dragGrab = signal({ x: 0, y: 0 });
  /** The player's zone on screen while a card is dragged, and whether the pointer is over it. */
  private readonly dropZoneRect = signal<HoverAnchor | null>(null);
  protected readonly dragOverZone = signal(false);
  /** The dragged hand card while it can still be played; `null` ends the drag's ghost. */
  protected readonly dragged = computed(() => {
    const drag = this.drag();
    const item =
      drag?.zone === 'HAND'
        ? this.hand().find((c) => c.item.instanceId === drag.instanceId)
        : undefined;
    return item !== undefined && item.action === 'play' ? item : null;
  });
  /** The character dragged to attack while it can still attack; `null` ends the drag. */
  protected readonly draggedAttacker = computed(() => {
    const drag = this.drag();
    const item =
      drag?.zone === 'PLAYER'
        ? this.playerBoard().find((c) => c.item.instanceId === drag.instanceId)
        : undefined;
    return item !== undefined && isAttackerAction(item.action) ? item.item.instanceId : null;
  });
  /** Where a dragged card that needs no target can be dropped: the player's zone. */
  protected readonly dropZone = computed(() =>
    this.dragged()?.item.requiresTarget === false ? this.dropZoneRect() : null,
  );
  /**
   * The attacker whose targets are offered: the dragged one, else the selected one. Dragging
   * selects nothing, so a cancelled drag leaves the selection as it was.
   */
  private readonly aimedAttacker = computed(
    () => this.draggedAttacker() ?? this.selectedAttacker(),
  );
  /** The legal attack targets of the aimed attacker, as sent by the backend. */
  private readonly attackTargets = computed(() => this.attackTargetsOf(this.aimedAttacker()));
  /**
   * The event card whose targets are offered: the dragged targeted event, else the selected one.
   * Dragging selects nothing, so a cancelled drag leaves target mode as it was.
   */
  private readonly aimedEvent = computed(() => {
    const drag = this.drag();
    const dragged =
      drag?.zone === 'HAND'
        ? this.match()?.player.hand.find((c) => c.instanceId === drag.instanceId)
        : undefined;
    return dragged?.requiresTarget ? dragged.instanceId : this.selectedEvent();
  });
  /**
   * The aim arrow from the attacker to the pointer, while an attacker is dragged or selected and
   * the pointer has moved; hidden while busy.
   */
  private readonly aim = signal<Aim | null>(null);
  protected readonly shownAim = computed(() => (this.busy() ? null : this.aim()));
  /** The legal targets of the aimed event card, as sent by the backend. */
  private readonly eventTargets = computed(() => {
    const eventId = this.aimedEvent();
    return this.match()?.player.hand.find((c) => c.instanceId === eventId)?.targets ?? [];
  });

  /** The player is aiming: an attacker or a targeted event is selected. */
  protected readonly targeting = computed(
    () => this.selectedAttacker() !== null || this.selectedEvent() !== null,
  );
  /** The pointer is over a legal target; the lifted card fades so the target stays visible. */
  protected readonly targetingHover = signal(false);
  /** The card hovered or focused for the hover card. */
  protected readonly previewed = signal<PreviewRef | null>(null);
  /**
   * Hand cards drawn since the previous view (instanceId → order among them), animated in from the
   * deck. Every hand card the player has seen in this match is in `seenHand`.
   */
  protected readonly drawn = signal<ReadonlyMap<number, number>>(new Map());
  private readonly seenHand = new Set<number>();
  private seenMatchId: string | null = null;

  protected readonly hand = computed(() =>
    (this.match()?.player.hand ?? []).map((card) =>
      this.cardItem('HAND', card, handProps(card, this.texts())),
    ),
  );
  protected readonly playerBoard = computed(() => this.boardItems('PLAYER'));
  protected readonly opponentBoard = computed(() => this.boardItems('OPPONENT'));
  protected readonly playerEmptySlots = computed(() =>
    emptySlots(this.match()?.player.board.length ?? 0),
  );
  protected readonly opponentEmptySlots = computed(() =>
    emptySlots(this.match()?.opponent.board.length ?? 0),
  );
  /** The accessible name of the opponent panel's target button; `null` while it is no target. */
  protected readonly opponentTargetLabel = computed(() => {
    const t = this.texts().matchTable;
    if (this.finished()) {
      return null;
    }
    if (this.aimedEvent() !== null) {
      return this.canTargetEvent(null, 'OPPONENT') ? t.targetCard(t.opponentZone) : null;
    }
    return this.aimedAttacker() !== null && this.canAttackTarget(null)
      ? t.attackCard(t.opponentZone)
      : null;
  });
  /** The accessible name of the player panel's target button; `null` while it is no target. */
  protected readonly playerTargetLabel = computed(() => {
    const t = this.texts().matchTable;
    return !this.finished() && this.aimedEvent() !== null && this.canTargetEvent(null, 'PLAYER')
      ? t.targetCard(t.playerZone)
      : null;
  });
  /** The selected attacker or targeted event card. */
  private readonly selectionProps = computed<CardProps | null>(() => {
    const match = this.match();
    if (match === null) {
      return null;
    }
    const attacker = match.player.board.find((c) => c.instanceId === this.selectedAttacker());
    if (attacker !== undefined) {
      return boardProps(attacker, this.texts());
    }
    const event = match.player.hand.find((c) => c.instanceId === this.selectedEvent());
    return event === undefined ? null : handProps(event, this.texts());
  });
  /**
   * The selected card lifted into the board centre while aiming; hidden while busy and while the
   * aim arrow (pointer aiming) already shows the attack, so the two never cross.
   */
  protected readonly liftedProps = computed(() =>
    this.busy() || this.shownAim() !== null ? null : this.selectionProps(),
  );

  /** Where the lifted card starts its flight, relative to the board centre (screen px). */
  protected readonly liftFrom = signal({ x: 0, y: 0 });

  /** Changes with the selection, so switching cards replays the fly-in. */
  protected readonly liftKey = computed(
    () => `${this.selectedAttacker() ?? ''}:${this.selectedEvent() ?? ''}`,
  );
  /**
   * The enlarged card for the hovered/focused card: its props, where it opens (below the
   * opponent's cards, above the player's) and a key that changes with the card, so a swap replays
   * the grow-in. Hidden while busy and when the card is gone from the view.
   */
  protected readonly hoverCard = computed(() => {
    const match = this.match();
    const ref = this.previewed();
    if (match === null || ref === null || this.busy() || this.drag() !== null) {
      return null;
    }
    const id = ref.instanceId;
    const texts = this.texts();
    let props: CardProps | null = null;
    if (ref.zone === 'HAND') {
      const card = match.player.hand.find((c) => c.instanceId === id);
      props = card ? handProps(card, texts) : null;
    } else if (ref.zone === 'PLAYER' || ref.zone === 'OPPONENT') {
      const side = ref.zone === 'PLAYER' ? match.player : match.opponent;
      const character = side.board.find((c) => c.instanceId === id);
      props = character ? boardProps(character, texts) : null;
    } else {
      const side = ref.zone === 'PLAYER_EVENT' ? match.player : match.opponent;
      const event = side.activeEvents.find((e) => e.instanceId === id);
      props = event ? eventProps(event) : null;
    }
    if (props === null) {
      return null;
    }
    const placement: HoverPlacement =
      ref.zone === 'OPPONENT' || ref.zone === 'OPPONENT_EVENT' ? 'below' : 'above';
    return { key: `${ref.zone}:${id}`, props, anchor: ref.anchor, placement };
  });

  /** The GET /current in flight; a newer one cancels it, so a stale response cannot win. */
  private loadRequest: Subscription | null = null;
  private replayTimer: ReturnType<typeof setTimeout> | null = null;
  /** A language change during playback refetches the table once playback ends. */
  private reloadAfterReplay = false;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  /** The match and turn the countdown runs for; a response for the same turn keeps it running. */
  private countdownTurn: string | null = null;
  /** The countdown hit zero while a request was in flight; the turn is ended once it settles. */
  private endTurnWhenIdle = false;
  /** The card pressed with the pointer; document listeners run only while it is set. */
  private press: DragPress | null = null;
  /** The click that follows a drag's release is the drag itself, not a second action. */
  private swallowClick = false;
  private tiltTimer: ReturnType<typeof setTimeout> | null = null;
  /** The pointer is followed for the aim arrow while an attacker is selected by click. */
  private aimTracking = false;
  /** The battle log container; it follows the newest entry unless the player scrolled away. */
  private readonly logBox = viewChild<ElementRef<HTMLElement>>('logBox');
  private logPinned = true;
  private logLastTop = 0;
  /** The attack being animated: a cue per element and the damage numbers; `null` when none. */
  private readonly attackFx = signal<{ cues: Map<string, FxCue>; floats: DamageFloat[] } | null>(
    null,
  );
  protected readonly damageFloats = computed(() => this.attackFx()?.floats ?? []);
  private fxKey = 0;
  private fxTimer: ReturnType<typeof setTimeout> | null = null;

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
    // A target button removed while hovered never fires mouseleave: reset once aiming ends.
    effect(() => {
      if (!this.targeting()) {
        untracked(() => this.targetingHover.set(false));
      }
    });
    // The dragged card lost its action (a move in flight, playback, time up, match over).
    effect(() => {
      if (this.drag() !== null && this.dragged() === null && this.draggedAttacker() === null) {
        untracked(() => this.cancelDrag());
      }
    });
    // A click-selected attacker aims at the pointer: follow it only while the selection lasts.
    effect(() => {
      const follow = this.selectedAttacker() !== null && !this.busy();
      untracked(() => this.trackAim(follow));
    });
    // New log entries: show the newest one, unless the player scrolled back through the log.
    afterRenderEffect(() => {
      const log = this.match()?.log;
      const box = this.logBox()?.nativeElement;
      if (log !== undefined && box !== undefined) {
        untracked(() => this.followLog(box));
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.endPress();
      this.trackAim(false);
      this.clearAttackFx();
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
    this.playOn(eventId, instanceId, side);
  }

  /** Plays the event on a character (`instanceId`) or on the player of `side`. */
  private playOn(eventId: number, instanceId: number | null, side: Side): void {
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

  protected selectAttacker(character: BoardCharacter): void {
    this.selectedEvent.set(null);
    this.selectedAttacker.set(character.instanceId);
  }

  protected cancelAttack(): void {
    this.selectedAttacker.set(null);
  }

  /**
   * `targetId === null` attacks the opponent player; the attacker is the selected one. The
   * response plays the attack animation, measured on the board as it is now.
   */
  protected attack(targetId: number | null, attackerId = this.selectedAttacker()): void {
    const match = this.match();
    if (match === null || attackerId === null) {
      return;
    }
    const attacker = this.fxElement('PLAYER', attackerId);
    const target = this.fxElement('OPPONENT', targetId);
    const snapshot: AttackSnapshot = {
      before: match,
      attackerId,
      targetId,
      attackerRect: attacker?.getBoundingClientRect() ?? null,
      targetRect: target?.getBoundingClientRect() ?? null,
    };
    this.move(
      this.gameService.attack(match.matchId, match.turn.number, attackerId, targetId),
      (after) => this.playAttack(snapshot, after),
    );
  }

  /** A side's board item, or its status panel for `instanceId === null`. */
  private fxElement(side: Side, instanceId: number | null): Element | null {
    const zone = side === 'PLAYER' ? 'player' : 'opponent';
    return this.host.nativeElement.querySelector(
      instanceId === null
        ? `app-status-panel.status.${zone}`
        : `.zone.${zone} .character[data-instance-id="${instanceId}"]`,
    );
  }

  /**
   * The attack animation for a response: the attacker lunges at the target, which shakes; the
   * damage numbers come from the HP before and after (a killed character lost what it had left).
   */
  private playAttack(snapshot: AttackSnapshot, after: MatchView): void {
    const { before, attackerId, targetId, attackerRect, targetRect } = snapshot;
    if (attackerRect === null || targetRect === null || after.replay.length > 1) {
      return;
    }
    const remaining = (board: BoardCharacter[], id: number | null) => {
      const c = board.find((candidate) => candidate.instanceId === id);
      return c === undefined ? 0 : c.health - c.damage;
    };
    const dealt =
      targetId === null
        ? before.opponent.hp - after.opponent.hp
        : remaining(before.opponent.board, targetId) - remaining(after.opponent.board, targetId);
    const taken =
      targetId === null
        ? 0
        : remaining(before.player.board, attackerId) - remaining(after.player.board, attackerId);
    const key = ++this.fxKey;
    const from = centre(attackerRect);
    const to = centre(targetRect);
    // Screen px; the lunge runs inside the zoomed board, so in board px.
    const scale = this.uiScale();
    const vector = { x: (to.x - from.x) / scale, y: (to.y - from.y) / scale };
    const cues = new Map<string, FxCue>([
      [`PLAYER:${attackerId}`, { key, role: 'lunge', vector }],
      [`OPPONENT:${targetId ?? 'panel'}`, { key, role: 'hit', vector }],
    ]);
    const floats: DamageFloat[] = [];
    if (dealt > 0) {
      floats.push({ key: `${key}:target`, x: to.x, y: targetRect.top, amount: dealt });
    }
    if (taken > 0) {
      floats.push({ key: `${key}:attacker`, x: from.x, y: attackerRect.top, amount: taken });
    }
    this.attackFx.set({ cues, floats });
    this.fxTimer = setTimeout(() => this.clearAttackFx(), ATTACK_FX_MS);
  }

  /** The attack cue of a board item or of a status panel (`instanceId === null`). */
  protected fxCue(side: Side, instanceId: number | null): FxCue | null {
    return this.attackFx()?.cues.get(`${side}:${instanceId ?? 'panel'}`) ?? null;
  }

  /** A character leaves the board: it fades out, after the impact if an attack is playing. */
  protected leaveCharacter(event: AnimationCallbackEvent): void {
    leaveBoard(event.target, event.animationComplete, this.attackFx() === null ? 0 : IMPACT_MS);
  }

  private clearAttackFx(): void {
    if (this.fxTimer !== null) {
      clearTimeout(this.fxTimer);
      this.fxTimer = null;
    }
    this.attackFx.set(null);
  }

  /**
   * The card is the action: one click plays a hand card, selects or cancels an attacker, attacks an
   * opponent character or targets a character with the selected event (see `cardAction`).
   */
  protected onCardClick(zone: CardZone, item: HandCard | BoardCharacter, event?: Event): void {
    if (this.swallowClick) {
      this.swallowClick = false;
      return;
    }
    const action = this.cardAction(zone, item);
    if (action === 'select' || action === 'play') {
      this.recordLiftOrigin(event?.currentTarget);
    }
    switch (action) {
      case 'play':
        this.play(item as HandCard);
        break;
      case 'cancelEvent':
        this.cancelTarget();
        break;
      case 'select':
        this.selectAttacker(item as BoardCharacter);
        // A pointer click aims from where it is (`detail` 0 is a keyboard click: the lifted card).
        if (event instanceof MouseEvent && event.detail > 0) {
          this.aimAt(item.instanceId, event.clientX, event.clientY);
        }
        break;
      case 'cancelAttack':
        this.cancelAttack();
        break;
      case 'attack':
        this.attack(item.instanceId);
        break;
      case 'target':
        this.chooseTarget(item.instanceId, zone === 'OPPONENT' ? 'OPPONENT' : 'PLAYER');
        break;
    }
  }

  /**
   * Offset (screen px) from the turn bar centre to the clicked card, so the lifted card
   * flies in from its slot instead of popping up in the middle.
   */
  private recordLiftOrigin(source?: EventTarget | null): void {
    if (!(source instanceof HTMLElement)) {
      this.liftFrom.set({ x: 0, y: 0 });
      return;
    }
    const bar = source.closest('main')?.querySelector('.turn-bar');
    if (!bar) {
      this.liftFrom.set({ x: 0, y: 0 });
      return;
    }
    const from = source.getBoundingClientRect();
    const to = bar.getBoundingClientRect();
    this.liftFrom.set({
      x: from.left + from.width / 2 - (to.left + to.width / 2),
      y: from.top + from.height / 2 - (to.top + to.height / 2),
    });
  }

  /** A click on the opponent panel: the attack or event target is the opponent player. */
  protected targetOpponent(): void {
    if (this.selectedEvent() !== null) {
      this.chooseTarget(null, 'OPPONENT');
    } else {
      this.attack(null);
    }
  }

  protected targetPlayer(): void {
    this.chooseTarget(null, 'PLAYER');
  }

  /**
   * Cursor of a hand or own board card button: cards that can be dragged can be grabbed; a dragged
   * attacker aims (`null` keeps the CSS one).
   */
  protected cardCursor(zone: DragZone, c: CardItem<HandCard | BoardCharacter>): string | null {
    if (!this.canDrag(zone, c.action)) {
      return null;
    }
    if (zone === 'PLAYER') {
      return this.draggedAttacker() === c.item.instanceId ? 'var(--cursor-target)' : 'grab';
    }
    return this.dragged()?.item.instanceId === c.item.instanceId ? 'grabbing' : 'grab';
  }

  /** A hand card is dragged to be played, an own character to attack. */
  protected canDrag(zone: DragZone, action: CardAction | null): boolean {
    return zone === 'HAND' ? action === 'play' : isAttackerAction(action);
  }

  /**
   * A primary-button (or touch) press on a hand card the player can play, or on an own character
   * that can attack, arms a drag. Below `DRAG_THRESHOLD_PX` of travel it stays a click; past it a
   * hand card follows the pointer, an attacker aims the arrow, and the release drops it (see
   * `dropCard` / `dropAttack`). Click and keyboard flows are untouched.
   */
  protected onCardPointerDown(
    zone: DragZone,
    item: HandCard | BoardCharacter,
    event: PointerEvent,
  ): void {
    if (
      this.press !== null ||
      event.button !== 0 ||
      !event.isPrimary ||
      !this.canDrag(zone, this.cardAction(zone, item)) ||
      !(event.currentTarget instanceof HTMLElement)
    ) {
      return;
    }
    const source = event.currentTarget;
    const rect = source.getBoundingClientRect();
    this.press = {
      zone,
      instanceId: item.instanceId,
      pointerId: event.pointerId,
      source,
      startX: event.clientX,
      startY: event.clientY,
      lastX: event.clientX,
      grab: { x: event.clientX - rect.left, y: event.clientY - rect.top },
      state: 'pressed',
    };
    const doc = this.host.nativeElement.ownerDocument;
    doc.addEventListener('pointermove', this.onPointerMove);
    doc.addEventListener('pointerup', this.onPointerUp);
    doc.addEventListener('pointercancel', this.onPointerCancel);
    doc.addEventListener('keydown', this.onDragKey);
    // Card art is a native image drag (which would cancel the pointer); text must not get selected.
    doc.addEventListener('dragstart', preventDefault);
    doc.addEventListener('selectstart', preventDefault);
  }

  private readonly onPointerMove = (event: PointerEvent): void => {
    const press = this.press;
    if (press === null || event.pointerId !== press.pointerId || press.state === 'cancelled') {
      return;
    }
    if (press.state === 'pressed') {
      const travel = Math.hypot(event.clientX - press.startX, event.clientY - press.startY);
      if (travel <= DRAG_THRESHOLD_PX) {
        return;
      }
      this.startDrag(press);
    }
    if (press.zone === 'PLAYER') {
      this.aimAt(press.instanceId, event.clientX, event.clientY);
      return;
    }
    const dx = event.clientX - press.lastX;
    press.lastX = event.clientX;
    const tilt = Math.max(-DRAG_TILT_MAX, Math.min(DRAG_TILT_MAX, dx * DRAG_TILT_PER_PX));
    this.dragPoint.set({ x: event.clientX, y: event.clientY, tilt });
    if (this.tiltTimer !== null) {
      clearTimeout(this.tiltTimer);
    }
    this.tiltTimer = setTimeout(() => {
      this.tiltTimer = null;
      this.dragPoint.update((point) => ({ ...point, tilt: 0 }));
    }, DRAG_TILT_REST_MS);
    const hit = this.elementAt(event.clientX, event.clientY);
    this.dragOverZone.set(hit?.closest('section.zone.player') != null);
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    const press = this.press;
    if (press === null || event.pointerId !== press.pointerId) {
      return;
    }
    const dragging = press.state === 'dragging';
    const card = dragging ? this.dragged()?.item : undefined;
    const attackerId = dragging ? this.draggedAttacker() : null;
    const wasClick = press.state === 'pressed';
    this.endPress();
    if (wasClick) {
      return;
    }
    // The click that follows this release belongs to the drag.
    this.swallowClick = true;
    setTimeout(() => (this.swallowClick = false));
    const hit = this.elementAt(event.clientX, event.clientY);
    if (card !== undefined) {
      this.dropCard(card, press.source, hit);
    } else if (attackerId !== null) {
      this.dropAttack(attackerId, hit);
    }
  };

  private readonly onPointerCancel = (event: PointerEvent): void => {
    if (this.press !== null && event.pointerId === this.press.pointerId) {
      this.endPress();
    }
  };

  private readonly onDragKey = (event: KeyboardEvent): void => {
    if (event.key === 'Escape' && this.press?.state === 'dragging') {
      event.preventDefault();
      this.cancelDrag();
    }
  };

  /**
   * The press travelled past the threshold: a hand card leaves its slot and follows the pointer;
   * an attacker stays in its slot and aims the arrow.
   */
  private startDrag(press: DragPress): void {
    press.state = 'dragging';
    try {
      // Keeps the pointer's events (and its cursor) on the card while it travels over the board.
      press.source.setPointerCapture?.(press.pointerId);
    } catch {
      // Not an active pointer (e.g. a synthetic event); the document listeners still follow it.
    }
    if (press.zone === 'HAND') {
      const zone = this.host.nativeElement.querySelector('section.zone.player');
      const rect = zone?.getBoundingClientRect();
      this.dropZoneRect.set(
        rect ? { left: rect.left, top: rect.top, width: rect.width, height: rect.height } : null,
      );
      this.dragGrab.set(press.grab);
    }
    this.hidePreview();
    this.drag.set({ zone: press.zone, instanceId: press.instanceId });
  }

  /** Ends the drag without a move; the release that follows clicks nothing. */
  private cancelDrag(): void {
    if (this.press !== null) {
      this.press.state = 'cancelled';
    }
    this.clearDrag();
  }

  /**
   * Drops a dragged card on the element under the pointer. A targeted event on one of its legal
   * targets (a character or a status panel) is played on it; any card on the player's zone does what
   * its click does (play, or target mode for a targeted event); anywhere else nothing happens.
   */
  private dropCard(card: HandCard, source: HTMLElement, hit: Element | null): void {
    if (hit === null || !this.host.nativeElement.contains(hit)) {
      return;
    }
    if (card.requiresTarget) {
      const target = dropTarget(hit);
      if (
        target !== null &&
        card.targets.some((t) => sameTarget(t, target.instanceId, target.side))
      ) {
        this.playOn(card.instanceId, target.instanceId, target.side);
        return;
      }
    }
    if (hit.closest('section.zone.player') !== null) {
      this.recordLiftOrigin(source);
      this.play(card);
    }
  }

  /** Drops a dragged attacker: on a legal attack target it attacks, anywhere else nothing. */
  private dropAttack(attackerId: number, hit: Element | null): void {
    const target = this.attackTargetAt(attackerId, hit);
    if (target !== null) {
      this.attack(target.instanceId, attackerId);
    }
  }

  /** The legal attack target of `attackerId` under the pointer (`hit`), if any. */
  private attackTargetAt(attackerId: number, hit: Element | null): DropTarget | null {
    if (hit === null || !this.host.nativeElement.contains(hit)) {
      return null;
    }
    const target = dropTarget(hit);
    return target !== null &&
      target.side === 'OPPONENT' &&
      this.attackTargetsOf(attackerId).some((t) => sameTarget(t, target.instanceId, 'OPPONENT'))
      ? target
      : null;
  }

  /** The legal attack targets of a player's character, as sent by the backend. */
  private attackTargetsOf(attackerId: number | null): Target[] {
    return this.match()?.player.board.find((c) => c.instanceId === attackerId)?.attackTargets ?? [];
  }

  /**
   * Points the aim arrow from the attacker's card to (x, y), or to the centre of the legal attack
   * target there (the arrow then locks on).
   */
  private aimAt(attackerId: number, x: number, y: number): void {
    const source = this.host.nativeElement.querySelector(
      `.zone.player .character[data-instance-id="${attackerId}"] .card-action`,
    );
    if (source === null) {
      this.aim.set(null);
      return;
    }
    const target = this.attackTargetAt(attackerId, this.elementAt(x, y));
    this.aim.set({
      from: centre(source.getBoundingClientRect()),
      to: target === null ? { x, y } : centre(target.element.getBoundingClientRect()),
      locked: target !== null,
    });
  }

  /** Follows the pointer for the arrow of a click-selected attacker (`on`), or stops. */
  private trackAim(on: boolean): void {
    if (on === this.aimTracking) {
      return;
    }
    this.aimTracking = on;
    const doc = this.host.nativeElement.ownerDocument;
    if (on) {
      doc.addEventListener('pointermove', this.onAimMove);
    } else {
      doc.removeEventListener('pointermove', this.onAimMove);
      if (this.drag()?.zone !== 'PLAYER') {
        this.aim.set(null);
      }
    }
  }

  private readonly onAimMove = (event: PointerEvent): void => {
    const attackerId = this.selectedAttacker();
    // A drag aims for itself (see onPointerMove).
    if (attackerId !== null && this.press?.state !== 'dragging') {
      this.aimAt(attackerId, event.clientX, event.clientY);
    }
  };

  /** The element under a viewport point; the ghost, drop area and arrow are click-through. */
  private elementAt(x: number, y: number): Element | null {
    // Optional: jsdom (specs) has no layout and no elementFromPoint.
    return this.host.nativeElement.ownerDocument.elementFromPoint?.(x, y) ?? null;
  }

  /** Forgets the press: drops the ghost or arrow and the document listeners. */
  private endPress(): void {
    const press = this.press;
    if (press === null) {
      return;
    }
    this.press = null;
    const doc = this.host.nativeElement.ownerDocument;
    doc.removeEventListener('pointermove', this.onPointerMove);
    doc.removeEventListener('pointerup', this.onPointerUp);
    doc.removeEventListener('pointercancel', this.onPointerCancel);
    doc.removeEventListener('keydown', this.onDragKey);
    doc.removeEventListener('dragstart', preventDefault);
    doc.removeEventListener('selectstart', preventDefault);
    if (press.source.hasPointerCapture?.(press.pointerId)) {
      press.source.releasePointerCapture(press.pointerId);
    }
    this.clearDrag();
  }

  private clearDrag(): void {
    if (this.tiltTimer !== null) {
      clearTimeout(this.tiltTimer);
      this.tiltTimer = null;
    }
    // A click-selected attacker's arrow comes back with the next pointer move.
    if (this.drag()?.zone === 'PLAYER') {
      this.aim.set(null);
    }
    this.drag.set(null);
    this.dragOverZone.set(false);
    this.dropZoneRect.set(null);
  }

  /** Called on every log scroll: scrolling up away from the newest entry stops following it. */
  protected onLogScroll(): void {
    const box = this.logBox()?.nativeElement;
    if (box === undefined) {
      return;
    }
    if (box.scrollHeight - box.scrollTop - box.clientHeight <= LOG_STICK_PX) {
      this.logPinned = true;
    } else if (box.scrollTop < this.logLastTop) {
      // Upwards only: the log's own smooth scroll to the newest entry moves down.
      this.logPinned = false;
    }
    this.logLastTop = box.scrollTop;
  }

  /** Scrolls the log (oldest first) to its newest entry at the bottom while it follows them. */
  private followLog(box: HTMLElement): void {
    if (!this.logPinned) {
      return;
    }
    const top = Math.max(0, box.scrollHeight - box.clientHeight);
    const view = box.ownerDocument.defaultView;
    // Optional: jsdom (specs) has no matchMedia and no Element.scrollTo.
    const smooth = view?.matchMedia?.('(prefers-reduced-motion: no-preference)').matches ?? false;
    this.logLastTop = box.scrollTop;
    if (smooth && typeof box.scrollTo === 'function') {
      box.scrollTo({ top, behavior: 'smooth' });
    } else {
      box.scrollTop = top;
      this.logLastTop = box.scrollTop;
    }
  }

  /** `event.currentTarget` is the hovered/focused card's `<li>`; the hover card opens at it. */
  protected showPreview(zone: PreviewRef['zone'], instanceId: number, event: Event): void {
    const source = event.currentTarget as Element;
    this.showPreviewAt(zone, instanceId, source.getBoundingClientRect());
  }

  protected showPreviewAt(zone: PreviewRef['zone'], instanceId: number, rect: DOMRect): void {
    const anchor = { left: rect.left, top: rect.top, width: rect.width, height: rect.height };
    this.previewed.set({ zone, instanceId, anchor });
  }

  protected hidePreview(): void {
    this.previewed.set(null);
  }

  protected onResize(): void {
    this.hidePreview();
    this.uiScale.set(boardScale());
  }

  /** A status panel's graveyard button: opens that side's graveyard, or closes it when open. */
  protected toggleGraveyard(side: Side): void {
    const kind: BoardOverlayKind = side === 'PLAYER' ? 'graveyard-player' : 'graveyard-opponent';
    if (this.openOverlay() === kind) {
      this.closeOverlay();
    } else {
      this.openOverlay.set(kind);
    }
  }

  /** Closes the open overlay and gives focus back to the button that opened it. */
  protected closeOverlay(): void {
    const kind = this.openOverlay();
    if (kind === null) {
      return;
    }
    this.openOverlay.set(null);
    const zone = kind === 'graveyard-player' ? 'player' : 'opponent';
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>(`.status.${zone} .graveyard-open`)
          ?.focus(),
      { injector: this.injector },
    );
  }

  /** Fades the lifted card while the pointer is over a legal target. */
  protected hoverTarget(action: CardAction | null, over: boolean): void {
    this.targetingHover.set(over && (action === 'attack' || action === 'target'));
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

  /** The legal action of a click on the card in this state; legality comes from the backend. */
  private cardAction(zone: CardZone, item: HandCard | BoardCharacter): CardAction | null {
    if (this.finished()) {
      return null;
    }
    if (zone === 'HAND') {
      const card = item as HandCard;
      if (!this.canPlay(card)) {
        return null;
      }
      return this.selectedEvent() === card.instanceId ? 'cancelEvent' : 'play';
    }
    if (this.aimedEvent() !== null) {
      return this.canTargetEvent(item.instanceId, zone) ? 'target' : null;
    }
    if (zone === 'OPPONENT') {
      return this.aimedAttacker() !== null && this.canAttackTarget(item.instanceId)
        ? 'attack'
        : null;
    }
    if (this.selectedAttacker() === item.instanceId) {
      return 'cancelAttack';
    }
    return (item as BoardCharacter).canAttack && this.myTurn() ? 'select' : null;
  }

  private cardItem<T extends HandCard | BoardCharacter>(
    zone: CardZone,
    item: T,
    props: CardProps,
  ): CardItem<T> {
    const action = this.cardAction(zone, item);
    const t = this.texts().matchTable;
    // Effective keywords: a Guard granted by an event counts, a removed one does not.
    const guard = zone !== 'HAND' && (item as BoardCharacter).keywords.includes('GUARD');
    const name = guard ? t.guardedName(item.name, t.keyword.GUARD) : item.name;
    let label: string;
    switch (action) {
      case 'play':
      case 'cancelEvent':
        label = t.playCard(name);
        break;
      case 'select':
      case 'cancelAttack':
        label = t.selectAttacker(name);
        break;
      case 'attack':
        label = t.attackCard(name);
        break;
      case 'target':
        label = t.targetCard(name);
        break;
      default:
        label = name;
    }
    return { item, props, action, label, guard };
  }

  private boardItems(side: Side): CardItem<BoardCharacter>[] {
    const match = this.match();
    if (match === null) {
      return [];
    }
    const board = side === 'PLAYER' ? match.player.board : match.opponent.board;
    const texts = this.texts();
    return board.map((character) => this.cardItem(side, character, boardProps(character, texts)));
  }

  /** A move request is in flight or the computer's moves are being played back. */
  protected busy(): boolean {
    return this.pending() || this.replaying();
  }

  /** `onResult` runs after a successful response is shown. */
  private move(request: Observable<MatchView>, onResult?: (match: MatchView) => void): void {
    // Guards a second click that lands before the disabled state is rendered.
    if (this.busy()) {
      return;
    }
    this.error.set(null);
    this.pending.set(true);
    request
      .pipe(
        finalize(() => {
          this.pending.set(false);
          this.endTurnIfTimeUp();
        }),
      )
      .subscribe({
        next: (match) => {
          this.clearSelection();
          this.show(match);
          onResult?.(match);
        },
        error: (error: unknown) => {
          this.clearSelection();
          if (!(error instanceof HttpErrorResponse)) {
            this.error.set('moveFailed');
          } else if (error.status === 409 || error.status === 404) {
            // 409: the view was stale (another turn or match) — refresh it silently.
            // 404: the match is gone (e.g. backend restart) — loadMatch sends the player to /new-match.
            // The refreshed view decides the turn — an end of turn owed by the countdown is dropped.
            this.endTurnWhenIdle = false;
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
    // A new view cuts a running attack animation to the new state.
    this.clearAttackFx();
    const steps = match.replay;
    if (steps.length <= 1) {
      this.setMatch(match);
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
      this.setMatch(stepView(match, steps[index]));
      this.replayReason.set(steps[index].reason);
      this.replayTimer = setTimeout(() => playStep(index + 1), this.replayStepMs);
    };
    playStep(0);
  }

  private finishReplay(match: MatchView): void {
    this.setMatch(match);
    this.updateCountdown(match);
    this.replaying.set(false);
    this.replayReason.set(null);
    if (this.reloadAfterReplay) {
      this.reloadAfterReplay = false;
      this.loadMatch();
    }
  }

  /**
   * Shows a view and marks the hand cards the player has not seen yet in this match as drawn
   * (a new match, or no match, starts afresh — the opening hand counts as drawn).
   */
  private setMatch(match: MatchView | null): void {
    if (match === null || match.matchId !== this.seenMatchId) {
      this.seenHand.clear();
      this.seenMatchId = match?.matchId ?? null;
    }
    const drawn = new Map<number, number>();
    for (const card of match?.player.hand ?? []) {
      if (!this.seenHand.has(card.instanceId)) {
        drawn.set(card.instanceId, drawn.size);
        this.seenHand.add(card.instanceId);
      }
    }
    this.drawn.set(drawn);
    this.match.set(match);
    if (drawn.size > 0) {
      afterNextRender(() => this.aimDraws(), { injector: this.injector });
    }
  }

  /** Points each drawn card's fly-in at the player's deck pile (offsets in screen px). */
  private aimDraws(): void {
    const root = this.host.nativeElement;
    const deck = root.querySelector('.aside.right .deck-pile');
    if (deck === null) {
      return;
    }
    const from = deck.getBoundingClientRect();
    for (const card of Array.from(root.querySelectorAll<HTMLElement>('.hand > li.drawn'))) {
      const to = card.getBoundingClientRect();
      card.style.setProperty(
        '--draw-x',
        `${from.left + from.width / 2 - (to.left + to.width / 2)}px`,
      );
      card.style.setProperty(
        '--draw-y',
        `${from.top + from.height / 2 - (to.top + to.height / 2)}px`,
      );
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
   * turn and stops it outside the player's turn. At zero the target selection is cancelled and the
   * turn is ended.
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
        this.timeUp();
      }
    }, 1000);
  }

  /**
   * The turn time is up: any attack or event target selection is cancelled and the turn is ended.
   * With a request in flight the end of turn waits until it settles (see `endTurnIfTimeUp`).
   */
  private timeUp(): void {
    this.clearSelection();
    this.endTurnWhenIdle = true;
    this.endTurnIfTimeUp();
  }

  /** Sends the end of turn owed by an expired countdown once nothing is in flight. */
  private endTurnIfTimeUp(): void {
    if (this.endTurnWhenIdle && this.countdownTurn !== null && !this.busy()) {
      this.endTurnWhenIdle = false;
      this.endTurn();
    }
  }

  private stopCountdown(): void {
    this.clearCountdownTimer();
    this.countdownTurn = null;
    this.endTurnWhenIdle = false;
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
          this.setMatch(null);
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

/** The board's zoom past the design size: the tighter of the width and height ratios, at least 1. */
function boardScale(): number {
  const scale = Math.min(window.innerWidth / DESIGN_WIDTH, window.innerHeight / DESIGN_HEIGHT);
  return Math.max(1, Math.round(scale * 1000) / 1000);
}

function emptySlots(used: number): number[] {
  return Array.from({ length: Math.max(0, BOARD_SLOTS - used) }, (_, index) => index);
}

/** The keyword line names the card's own ability keyword. */
function handProps(card: HandCard, texts: Messages): CardProps {
  return {
    cardId: card.cardId,
    faction: card.faction,
    type: card.type,
    name: card.name,
    cost: card.cost,
    attack: card.attack,
    health: card.health,
    baseAttack: card.attack,
    baseHealth: card.health,
    damaged: false,
    keyword: card.ability ? texts.matchTable.keyword[card.ability.keyword] : null,
    ability: card.ability?.text ?? null,
    text: card.text,
    effects: card.effects.map((effect) => effect.text),
    duration: card.duration,
    turnsLeft: null,
    trapped: false,
  };
}

/** Displayed health is the remaining HP; the keyword line lists the effective keywords. */
function boardProps(character: BoardCharacter, texts: Messages): CardProps {
  const keywords = character.keywords.map((keyword) => texts.matchTable.keyword[keyword]);
  return {
    cardId: character.cardId,
    faction: character.faction,
    type: 'CHARACTER',
    name: character.name,
    cost: character.cost,
    attack: character.attack,
    health: character.health - character.damage,
    baseAttack: character.baseAttack,
    baseHealth: character.baseHealth,
    damaged: character.damage > 0,
    keyword: keywords.length > 0 ? keywords.join(', ') : null,
    ability: character.ability?.text ?? null,
    text: null,
    effects: [],
    duration: null,
    turnsLeft: null,
    trapped: character.trapped,
  };
}

function eventProps(event: ActiveEvent): CardProps {
  return {
    cardId: event.cardId,
    faction: event.faction,
    type: 'EVENT',
    name: event.name,
    cost: event.cost,
    attack: null,
    health: null,
    baseAttack: null,
    baseHealth: null,
    damaged: false,
    keyword: null,
    ability: null,
    text: null,
    effects: event.effects.map((effect) => effect.text),
    duration: null,
    turnsLeft: event.turnsLeft,
    trapped: false,
  };
}

function preventDefault(event: Event): void {
  event.preventDefault();
}

function isAttackerAction(action: CardAction | null): boolean {
  return action === 'select' || action === 'cancelAttack';
}

function centre(rect: DOMRect): Point {
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

/** The character (board `li[data-instance-id]`) or status panel a card is dropped on, if any. */
function dropTarget(hit: Element): DropTarget | null {
  const panel = hit.closest('app-status-panel');
  if (panel !== null) {
    return {
      instanceId: null,
      side: panel.classList.contains('opponent') ? 'OPPONENT' : 'PLAYER',
      element: panel,
    };
  }
  const character = hit.closest<HTMLElement>('.board .character[data-instance-id]');
  const zone = character?.closest('section.zone');
  if (!character || !zone) {
    return null;
  }
  return {
    instanceId: Number(character.dataset['instanceId']),
    side: zone.classList.contains('opponent') ? 'OPPONENT' : 'PLAYER',
    element: character,
  };
}

/** `instanceId === null` means the player of `side`; a character target ignores `side`. */
function sameTarget(target: Target, instanceId: number | null, side: Side): boolean {
  return instanceId === null
    ? target.kind === 'PLAYER' && target.side === side
    : target.kind === 'CHARACTER' && target.instanceId === instanceId;
}
